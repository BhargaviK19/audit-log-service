package com.schwab.auditlog.service;

import com.schwab.auditlog.dto.ChainViolation;
import com.schwab.auditlog.dto.VerificationResponse;
import com.schwab.auditlog.model.AuditEvent;
import com.schwab.auditlog.model.ViolationType;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks the full chain in sequence order and re-derives every hash from the stored fields,
 * comparing it against what's stored. This is intentionally a full re-derivation, not a
 * trust-the-stored-hash check -- the entire point is that we do not trust anything read
 * from the database without recomputing it.
 *
 * Cost: O(n) reads and O(n) hash computations per call, streamed via a cursor so the whole
 * collection is never held in memory at once. For very large logs this is the natural place
 * to add periodic "checkpoint" verification (verify since the last known-good checkpoint)
 * rather than always walking from record 1 -- noted as a scoped-out optimization in the README.
 */
@Service
public class ChainVerificationService {

    private final MongoTemplate mongoTemplate;
    private final HashingService hashingService;
    private final String genesisHash;

    public ChainVerificationService(MongoTemplate mongoTemplate, HashingService hashingService, AuditLogService auditLogService) {
        this.mongoTemplate = mongoTemplate;
        this.hashingService = hashingService;
        this.genesisHash = auditLogService.getGenesisHash();
    }

    public VerificationResponse verify() {
        List<ChainViolation> violations = new ArrayList<>();

        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "sequenceNumber"));
        try (var cursor = mongoTemplate.stream(query, AuditEvent.class)) {
            long expectedSequence = 1L;
            String expectedPreviousHash = genesisHash;
            long checked = 0;

            var iterator = cursor.iterator();
            while (iterator.hasNext()) {
                AuditEvent record = iterator.next();
                checked++;

                if (record.getSequenceNumber() != expectedSequence) {
                    violations.add(new ChainViolation(expectedSequence, null, ViolationType.SEQUENCE_GAP,
                            "Expected sequenceNumber " + expectedSequence + " but found " + record.getSequenceNumber() +
                                    " -- one or more records appear to be missing."));
                    expectedSequence = record.getSequenceNumber();
                }

                if (expectedSequence == 1L && !genesisHash.equals(record.getPreviousHash())) {
                    violations.add(new ChainViolation(record.getSequenceNumber(), record.getId(), ViolationType.INVALID_GENESIS,
                            "First record's previousHash does not match the configured genesis value."));
                }

                String recomputedContentHash = hashingService.computeContentHash(
                        record.getSequenceNumber(), record.getEventType(), record.getActorId(),
                        record.getResourceType(), record.getResourceId(), record.getPayload(),
                        record.getTimestamp(), record.getClientSuppliedEventTime());

                if (!recomputedContentHash.equals(record.getContentHash())) {
                    violations.add(new ChainViolation(record.getSequenceNumber(), record.getId(), ViolationType.CONTENT_HASH_MISMATCH,
                            "Recomputed content hash does not match the stored contentHash -- one or more fields were modified after the record was written."));
                }

                String recomputedChainHash = hashingService.computeChainHash(record.getContentHash(), record.getPreviousHash());
                if (!recomputedChainHash.equals(record.getChainHash())) {
                    violations.add(new ChainViolation(record.getSequenceNumber(), record.getId(), ViolationType.CHAIN_HASH_MISMATCH,
                            "Recomputed chainHash does not match the stored chainHash."));
                }

                if (expectedSequence != 1L && !expectedPreviousHash.equals(record.getPreviousHash())) {
                    violations.add(new ChainViolation(record.getSequenceNumber(), record.getId(), ViolationType.PREVIOUS_HASH_MISMATCH,
                            "This record's previousHash does not match the prior record's chainHash -- the chain was relinked, reordered, or a prior record was deleted/replaced."));
                }

                expectedPreviousHash = record.getChainHash();
                expectedSequence++;
            }

            boolean intact = violations.isEmpty();
            ChainViolation first = violations.stream()
                    .min((a, b) -> Long.compare(a.getSequenceNumber(), b.getSequenceNumber()))
                    .orElse(null);

            return new VerificationResponse(intact, checked, Instant.now(), violations, first);
        }
    }
}