package com.bank.accounts.domain.exception;

/** The client is unknown to this service, inactive or deleted (F1-AC6, F2-AC7). */
public class ClientUnavailableException extends DomainException {

    public ClientUnavailableException(String message) {
        super(message);
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
        return "Cliente no disponible";
    }
}
