package com.bank.accounts.domain.exception;

/** A domain invariant was violated. */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "BUSINESS_RULE_VIOLATION";
    }

    @Override
    public ErrorCategory category() {
        return ErrorCategory.BUSINESS_RULE;
    }

    @Override
    public String title() {
        return "Business rule violation";
    }
}
