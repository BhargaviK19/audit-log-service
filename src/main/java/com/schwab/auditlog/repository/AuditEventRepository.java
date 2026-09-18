package com.schwab.auditlog.repository;

import com.schwab.auditlog.model.AuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Deliberately exposes ONLY read and insert operations.
 *
 * There is no save-for-update path used anywhere in this codebase: MongoRepository.save()
 * technically supports upsert-by-id, but AuditLogService only ever calls insert(), which
 * fails fast if an _id or unique index collides rather than silently overwriting a record.
 * No delete* method is called anywhere in main code. This interface is the full surface
 * this service uses against the audit_events collection -- there is nothing to remove to
 * "turn off" mutation; it was never wired up.
 */
public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {

    Optional<AuditEvent> findTopByOrderBySequenceNumberDesc();

    long countBySequenceNumberLessThanEqual(long sequenceNumber);
}