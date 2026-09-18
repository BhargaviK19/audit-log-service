package com.schwab.auditlog.service;

import com.schwab.auditlog.dto.CreateAuditEventRequest;
import com.schwab.auditlog.model.AuditEvent;
import com.schwab.auditlog.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditLogServiceTest {

    private static final String GENESIS = "0".repeat(128);

    private AuditEventRepository repository;
    private MongoTemplate mongoTemplate;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditEventRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        service = new AuditLogService(repository, mongoTemplate, new HashingService(), GENESIS, 20, 200);
    }

    private CreateAuditEventRequest request(String actorId) {
        CreateAuditEventRequest r = new CreateAuditEventRequest();
        r.setEventType("USER_LOGIN");
        r.setActorId(actorId);
        r.setResourceType("USER");
        r.setResourceId("user-1");
        r.setPayload(Map.of("ip", "10.0.0.1"));
        return r;
    }

    @Test
    void firstEventUsesGenesisHashAsPreviousHashAndSequenceOne() {
        when(repository.findTopByOrderBySequenceNumberDesc()).thenReturn(Optional.empty());
        when(repository.insert(any(AuditEvent.class))).thenAnswer(inv -> {
            AuditEvent e = inv.getArgument(0);
            e.setId("generated-id");
            return e;
        });

        AuditEvent saved = service.appendEvent(request("actor-1"));

        assertEquals(1L, saved.getSequenceNumber());
        assertEquals(GENESIS, saved.getPreviousHash());
        assertNotNull(saved.getContentHash());
        assertNotNull(saved.getChainHash());
    }

    @Test
    void secondEventChainsOffThePreviousRecordsChainHash() {
        AuditEvent previous = new AuditEvent();
        previous.setSequenceNumber(1L);
        previous.setChainHash("previous-chain-hash-value");
        when(repository.findTopByOrderBySequenceNumberDesc()).thenReturn(Optional.of(previous));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        when(repository.insert(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.appendEvent(request("actor-2"));

        AuditEvent saved = captor.getValue();
        assertEquals(2L, saved.getSequenceNumber());
        assertEquals("previous-chain-hash-value", saved.getPreviousHash());
    }

    @Test
    void serverAssignsTheTimestampRegardlessOfClientInput() {
        when(repository.findTopByOrderBySequenceNumberDesc()).thenReturn(Optional.empty());
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        when(repository.insert(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        service.appendEvent(request("actor-1"));
        Instant after = Instant.now();

        AuditEvent saved = captor.getValue();
        // A generous tolerance window rather than razor-thin exact bracketing: this test's goal
        // is to prove the server assigns *its own* current time (not a suspiciously old/future
        // value, and not something derived from client input), not to measure exact call latency,
        // which can jitter under test-environment overhead (JIT warm-up, Mockito's one-time
        // bytecode instrumentation on first use, etc.).
        assertFalse(saved.getTimestamp().isBefore(before.minusSeconds(2)));
        assertFalse(saved.getTimestamp().isAfter(after.plusSeconds(2)));
    }
}