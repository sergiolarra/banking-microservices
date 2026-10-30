package com.bank.clients.domain.exception;

import java.util.UUID;

/** The requested resource does not exist. */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException client(UUID clientId) {
        return new ResourceNotFoundException("No client exists with clientId " + clientId);
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
