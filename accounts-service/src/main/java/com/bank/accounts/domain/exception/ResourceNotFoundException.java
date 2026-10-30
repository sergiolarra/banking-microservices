package com.bank.accounts.domain.exception;

/** The requested resource does not exist. */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException account(String accountNumber) {
        return new ResourceNotFoundException("No account exists with number " + accountNumber);
    }

    public static ResourceNotFoundException movement(long id) {
        return new ResourceNotFoundException("No movement exists with id " + id);
    }

    @Override
    public String code() {
        return "RESOURCE_NOT_FOUND";
    }

    @Override
    public ErrorCategory category() {
        return ErrorCategory.NOT_FOUND;
    }

    @Override
    public String title() {
        return "Resource not found";
    }
}
