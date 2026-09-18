package com.schwab.auditlog.exception;

/** Container for the small set of exceptions this service can throw. */
public class AuditLogExceptions {

    private AuditLogExceptions() {}

    /** Thrown when the append lock could not be acquired in time (backpressure signal to the caller). */
    public static class AppendTimeoutException extends RuntimeException {
        public AppendTimeoutException(String message) {
            super(message);
        }
    }

    /** Thrown when a chain-integrity invariant we expect to hold internally is violated
     *  (e.g. unique sequence index conflict slipping through the append lock). This indicates
     *  a bug or concurrent out-of-band write, not bad caller input. */
    public static class ChainIntegrityException extends RuntimeException {
        public ChainIntegrityException(String message) {
            super(message);
        }
        public ChainIntegrityException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Thrown for invalid caller input that bean validation doesn't already catch (e.g. bad time range). */
    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }
}