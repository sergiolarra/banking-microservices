package com.bank.accounts.domain.exception;

import java.util.UUID;

/** The client is unknown to this service, inactive or deleted (F1-AC6, F2-AC7). */
public class ClientUnavailableException extends DomainException {

    public ClientUnavailableException(String message) {
        super(message);
    }

    /** The client has not reached this service yet, or never existed (eventual consistency, SPEC §10). */
    public static ClientUnavailableException unknown(UUID clientId) {
        return new ClientUnavailableException("Client %s is not known to this service".formatted(clientId));
    }

    public static ClientUnavailableException inactiveOrDeleted(UUID clientId) {
        return new ClientUnavailableException("Client %s is inactive or has been deleted".formatted(clientId));
    }

    @Override
    public String code() {
        return "CLIENT_UNAVAILABLE";
    }

    @Override
    public ErrorCategory category() {
        return ErrorCategory.BUSINESS_RULE;
    }

    @Override
    public String title() {
        return "Client unavailable";
    }
}
