package com.bank.accounts.domain.exception;

/** Only the latest movement of an account can be corrected (ADR-012, F2-AC8). */
public class MovementNotModifiableException extends DomainException {

    public MovementNotModifiableException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "MOVEMENT_NOT_MODIFIABLE";
    }

    @Override
    public ErrorCategory category() {
        return ErrorCategory.CONFLICT;
    }

    @Override
    public String title() {
        return "Movement not modifiable";
    }
}
