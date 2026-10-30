package com.bank.accounts.domain.exception;

/** Movements cannot be registered or corrected on an inactive account (F2-AC7). */
public class InactiveAccountException extends DomainException {

    public InactiveAccountException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "INACTIVE_ACCOUNT";
    }

    @Override
    public ErrorCategory category() {
        return ErrorCategory.BUSINESS_RULE;
    }

    @Override
    public String title() {
        return "Cuenta inactiva";
    }
}
