package com.bank.clients.domain.exception;

/**
 * Base type for every error raised by the domain. Each subclass declares its own
 * code, category and title, so adding a new error does not require changing the
 * exception handler (ADR-024, ADR-026).
 *
 * <p>Messages are user-facing and therefore written in Spanish (ADR-023).
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    /** Stable, English, machine-readable error code (SPEC §8). */
    public abstract String code();

    public abstract ErrorCategory category();

    /** Short, user-facing summary of the error. */
    public abstract String title();
}
