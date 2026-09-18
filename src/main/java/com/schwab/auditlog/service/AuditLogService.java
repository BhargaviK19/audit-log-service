package com.schwab.auditlog.service;

import com.schwab.auditlog.dto.CreateAuditEventRequest;
import com.schwab.auditlog.dto.PageResponse;
import com.schwab.auditlog.exception.AuditLogExceptions.AppendTimeoutException;
import com.schwab.auditlog.exception.AuditLogExceptions.ChainIntegrityException;
import com.schwab.auditlog.exception.AuditLogExceptions.InvalidRequestException;
import com.schwab.auditlog.model.AuditEvent;
import com.schwab.auditlog.repository.AuditEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns the two write-time invariants that make the log trustworthy:
 *   1. Every record gets the next sequence number, with no gaps.
 *   2. Every record's previousHash is exactly the chainHash of the record before it.
 *
 * Concurrency model (documented trade-off -- see docs/ARCHITECTURE.md "Concurrency &
 * scaling"): appends are serialized through an in-process ReentrantLock. That is correct
 * and sufficient for a single-instance deployment (which is what this prototype targets),
 * but it does NOT provide mutual exclusion across multiple service instances. A unique
 * index on sequenceNumber is kept as a hard backstop: if two writers ever raced past the
 * lock (e.g. a future horizontally-scaled deployment before this is fixed), the loser's
 * insert fails fast with a DuplicateKeyException instead of silently corrupting the chain.
 * Scaling this out for real would mean either (a) a single designated writer per chain /
 * partition, (b) moving the append to a Mongo transaction with optimistic retry keyed off
 * an expected previousHash, or (c) fronting writes with a durable, ordered log (e.g. Kafka)
 * and having a single consumer perform the chain append.
 */
@Service
public class AuditLogService {

    private final AuditEventRepository repository;
    private final MongoTemplate mongoTemplate;
    private final HashingService hashingService;
    private final String genesisHash;
    private final int defaultPageSize;
    private final int maxPageSize;

    private final ReentrantLock appendLock = new ReentrantLock(true); // fair lock: FIFO under contention

    public AuditLogService(AuditEventRepository repository,
                           MongoTemplate mongoTemplate,
                           HashingService hashingService,
                           @Value("${audit.genesis-hash}") String genesisHash,
                           @Value("${audit.default-page-size}") int defaultPageSize,
                           @Value("${audit.max-page-size}") int maxPageSize) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.hashingService = hashingService;
        this.genesisHash = genesisHash;
        this.defaultPageSize = defaultPageSize;
        this.maxPageSize = maxPageSize;
    }

    /**
     * Appends one new, immutable record to the end of the chain. Blocking, but bounded:
     * waits up to 5 seconds for the append lock before failing loudly with a 503 rather
     * than queuing writers indefinitely.
     */
    public AuditEvent appendEvent(CreateAuditEventRequest request) {
        boolean acquired;
        try {
            acquired = appendLock.tryLock(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppendTimeoutException("Interrupted while waiting to append");
        }
        if (!acquired) {
            throw new AppendTimeoutException("Timed out waiting to append to the audit log; retry shortly");
        }
        try {
            Optional<AuditEvent> last = repository.findTopByOrderBySequenceNumberDesc();
            long nextSequenceNumber = last.map(e -> e.getSequenceNumber() + 1).orElse(1L);
            String previousHash = last.map(AuditEvent::getChainHash).orElse(genesisHash);

            Instant timestamp = Instant.now(); // server-assigned; see docs/ARCHITECTURE.md

            String contentHash = hashingService.computeContentHash(
                    nextSequenceNumber, request.getEventType(), request.getActorId(),
                    request.getResourceType(), request.getResourceId(), request.getPayload(),
                    timestamp, request.getClientSuppliedEventTime());
            String chainHash = hashingService.computeChainHash(contentHash, previousHash);

            AuditEvent event = new AuditEvent(
                    null, nextSequenceNumber, request.getEventType(), request.getActorId(),
                    request.getResourceType(), request.getResourceId(), request.getPayload(),
                    timestamp, request.getClientSuppliedEventTime(), contentHash, previousHash, chainHash);

            try {
                return repository.insert(event);
            } catch (DuplicateKeyException e) {
                // Backstop firing: something inserted at this sequence number despite the lock
                // (e.g. a second instance running against the same DB). Fail loudly rather than
                // silently accept a chain we can no longer be sure is correctly ordered.
                throw new ChainIntegrityException(
                        "Sequence number " + nextSequenceNumber + " was already taken; refusing to append. " +
                                "This indicates a concurrent writer outside this lock (see docs/ARCHITECTURE.md).", e);
            }
        } finally {
            appendLock.unlock();
        }
    }

    /** Flexible filtered + paginated read. All filters are optional and combine with AND. */
    public PageResponse<AuditEvent> query(String actorId, String resourceType, String resourceId,
                                          String eventType, Instant from, Instant to,
                                          Integer page, Integer size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("'from' must not be after 'to'");
        }
        int pageNum = page == null ? 0 : Math.max(page, 0);
        int pageSize = size == null ? defaultPageSize : size;
        if (pageSize <= 0 || pageSize > maxPageSize) {
            throw new InvalidRequestException("size must be between 1 and " + maxPageSize);
        }

        Criteria criteria = new Criteria();
        List<Criteria> clauses = new java.util.ArrayList<>();
        if (actorId != null && !actorId.isBlank()) clauses.add(Criteria.where("actorId").is(actorId));
        if (resourceType != null && !resourceType.isBlank()) clauses.add(Criteria.where("resourceType").is(resourceType));
        if (resourceId != null && !resourceId.isBlank()) clauses.add(Criteria.where("resourceId").is(resourceId));
        if (eventType != null && !eventType.isBlank()) clauses.add(Criteria.where("eventType").is(eventType));
        if (from != null || to != null) {
            Criteria timeCriteria = Criteria.where("timestamp");
            if (from != null) timeCriteria = timeCriteria.gte(from);
            if (to != null) timeCriteria = timeCriteria.lte(to);
            clauses.add(timeCriteria);
        }
        if (!clauses.isEmpty()) {
            criteria = criteria.andOperator(clauses.toArray(new Criteria[0]));
        }

        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "sequenceNumber"))
                .skip((long) pageNum * pageSize)
                .limit(pageSize);

        List<AuditEvent> items = mongoTemplate.find(query, AuditEvent.class);
        long total = mongoTemplate.count(new Query(criteria), AuditEvent.class);

        return new PageResponse<>(items, pageNum, pageSize, total);
    }

    public Optional<AuditEvent> findById(String id) {
        return repository.findById(id);
    }

    public String getGenesisHash() {
        return genesisHash;
    }
}