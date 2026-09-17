package com.schwab.auditlog.model;

/** Classifies the kind of tamper evidence found while walking the chain. */
public enum ViolationType {
    /** The record's stored contentHash no longer matches a hash recomputed from its fields. */
    CONTENT_HASH_MISMATCH,
    /** The record's stored chainHash no longer matches recompute(contentHash + previousHash). */
    CHAIN_HASH_MISMATCH,
    /** This record's previousHash does not equal the prior record's chainHash (chain relinked, reordered, or a record was deleted). */
    PREVIOUS_HASH_MISMATCH,
    /** A gap in sequenceNumber was found (a record is missing outright). */
    SEQUENCE_GAP,
    /** The first record's previousHash is not the configured genesis value. */
    INVALID_GENESIS
}