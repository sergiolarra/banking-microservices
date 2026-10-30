package com.bank.accounts.domain.exception;

/** The resource would break a uniqueness rule. */
public class DuplicateResourceException extends DomainException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public static DuplicateResourceException accountNumber(String accountNumber) {
        return new DuplicateResourceException("An account with number " + accountNumber + " already exists");
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
