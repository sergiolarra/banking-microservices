package com.bank.clients.domain.exception;

/** The resource would break a uniqueness rule. */
public class DuplicateResourceException extends DomainException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public static DuplicateResourceException identification(String identification) {
        return new DuplicateResourceException("A client with identification " + identification + " already exists");
    }

    @Override
    public String code() {
        return "DUPLICATE_RESOURCE";
    }

    @Override
    public ErrorCategory category() {
        return ErrorCategory.CONFLICT;
    }

    @Override
    public String title() {
        return "Duplicate resource";
    }
}
