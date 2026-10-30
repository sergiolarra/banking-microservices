package com.bank.accounts.domain.exception;

/** The report date range is malformed or too wide (F4-AC4). */
public class InvalidDateRangeException extends DomainException {

    public InvalidDateRangeException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "INVALID_PARAMETER";
    }

    @Override
    public ErrorCategory category() {
        return ErrorCategory.INVALID_INPUT;
    }

    @Override
    public String title() {
        return "Parámetro inválido";
    }
}
