package com.bank.accounts.domain.exception;

import java.util.UUID;

/** The requested resource does not exist. */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException account(String accountNumber) {
        return new ResourceNotFoundException("No account exists with number " + accountNumber);
    }

    /** F4-AC5: the client is not in the local replica. */
    public static ResourceNotFoundException client(UUID clientId) {
        return new ResourceNotFoundException("No client exists with clientId " + clientId);
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
