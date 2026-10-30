package com.bank.accounts.domain.exception;

/** F3: the movement would leave the account with a negative balance. */
public class InsufficientFundsException extends DomainException {

    /** Exact text required by the brief (F3-AC1). */
    public static final String MESSAGE = "Saldo no disponible";

    public InsufficientFundsException() {
        super(MESSAGE);
    }

    @Override
    public String code() {
        return "INSUFFICIENT_FUNDS";
    }

    @Override
    public ErrorCategory category() {
        return ErrorCategory.BUSINESS_RULE;
    }

    @Override
    public String title() {
        return MESSAGE;
    }
}
