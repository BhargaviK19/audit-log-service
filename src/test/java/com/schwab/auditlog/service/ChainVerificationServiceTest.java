package com.schwab.auditlog.service;

import com.schwab.auditlog.dto.VerificationResponse;
import com.schwab.auditlog.model.AuditEvent;
import com.schwab.auditlog.model.ViolationType;
import com.schwab.auditlog.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChainVerificationServiceTest {

    private static final String GENESIS = "0".repeat(128);

    private HashingService hashingService;
    private MongoTemplate mongoTemplate;
    private ChainVerificationService verificationService;

    @BeforeEach
    void setUp() {
        hashingService = new HashingService();
        mongoTemplate = mock(MongoTemplate.class);

        AuditEventRepository repository = mock(AuditEventRepository.class);
        AuditLogService auditLogService = new AuditLogService(repository, mongoTemplate, hashingService, GENESIS, 20, 200);
        verificationService = new ChainVerificationService(mongoTemplate, hashingService, auditLogService);
    }

    /** Builds a valid, correctly-linked N-record chain the same way AuditLogService would. */
    private List<AuditEvent> buildValidChain(int count) {
        List<AuditEvent> events = new java.util.ArrayList<>();
        String previousHash = GENESIS;
        for (int i = 1; i <= count; i++) {
            Instant ts = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i);
            Map<String, Object> payload = Map.of("i", i);
            String contentHash = hashingService.computeContentHash(i, "EVT", "actor", "TYPE", "res-" + i, payload, ts, null);
            String chainHash = hashingService.computeChainHash(contentHash, previousHash);

            AuditEvent e = new AuditEvent(String.valueOf(i), i, "EVT", "actor", "TYPE", "res-" + i,
                    payload, ts, null, contentHash, previousHash, chainHash);
            events.add(e);
            previousHash = chainHash;
        }
        return events;
    }

    /** ChainVerificationService reads via mongoTemplate.stream(...), which returns a
     *  java.util.stream.Stream<AuditEvent> in this Spring Data version -- mock that directly
     *  rather than an older CloseableIterator-based signature. */
    @SuppressWarnings("unchecked")
    private void mockStream(List<AuditEvent> events) {
        when(mongoTemplate.stream(any(Query.class), org.mockito.ArgumentMatchers.eq(AuditEvent.class)))
                .thenReturn(events.stream());
    }

    @Test
    void anUntamperedChainVerifiesAsIntact() {
        mockStream(buildValidChain(5));

        VerificationResponse result = verificationService.verify();

        assertTrue(result.isIntact());
        assertEquals(5, result.getRecordsChecked());
        assertTrue(result.getViolations().isEmpty());
    }

    @Test
    void modifyingAPayloadFieldIsDetectedAsContentHashMismatch() {
        List<AuditEvent> events = buildValidChain(5);
        // Tamper with record #3's payload directly, as if someone edited the DB by hand,
        // WITHOUT recomputing its hashes -- exactly the attack this endpoint exists to catch.
        events.get(2).setPayload(Map.of("i", "TAMPERED"));
        mockStream(events);

        VerificationResponse result = verificationService.verify();

        assertFalse(result.isIntact());
        assertNotNull(result.getFirstViolation());
        assertEquals(3L, result.getFirstViolation().getSequenceNumber());
        assertEquals(ViolationType.CONTENT_HASH_MISMATCH, result.getFirstViolation().getViolationType());
    }

    @Test
    void deletingARecordIsDetectedAsASequenceGap() {
        List<AuditEvent> events = buildValidChain(5);
        events.remove(2); // remove record #3 entirely, simulating a direct DB delete
        mockStream(events);

        VerificationResponse result = verificationService.verify();

        assertFalse(result.isIntact());
        assertTrue(result.getViolations().stream().anyMatch(v -> v.getViolationType() == ViolationType.SEQUENCE_GAP));
    }

    @Test
    void reattachingLaterRecordsAfterADeletionIsDetectedAsChainHashMismatch() {
        // A more sophisticated tamper attempt: delete record #3 AND re-point record #4's
        // previousHash to record #2's chainHash so there's no sequence gap. This must still
        // be caught: what the attacker cannot do without redoing the entire rest of the chain
        // is keep record #4's own chainHash consistent, since chainHash depends on
        // previousHash. We simulate the realistic partial-tamper case: the attacker fixes
        // #4's previousHash but does not (or cannot, without invalidating everything after)
        // recompute #4's chainHash to match -- which this check catches.
        List<AuditEvent> events = buildValidChain(5);
        AuditEvent recordTwo = events.get(1);
        AuditEvent recordThree = events.get(2);
        AuditEvent recordFour = events.get(3);

        events.remove(recordThree);
        recordFour.setPreviousHash(recordTwo.getChainHash());
        mockStream(events);

        VerificationResponse result = verificationService.verify();

        assertFalse(result.isIntact());
        assertTrue(result.getViolations().stream().anyMatch(v -> v.getViolationType() == ViolationType.CHAIN_HASH_MISMATCH));
    }
}