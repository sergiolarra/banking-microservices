package com.bank.accounts.infrastructure.rest.dto;

/** Input rules of the account and movement requests (SPEC §4.3, §4.4, §10). Messages are in English (ADR-027). */
final class AccountConstraints {

    static final String ACCOUNT_NUMBER_REGEX = "^[0-9]{6,10}$";

    static final String ACCOUNT_NUMBER_REQUIRED = "Account number is required";
    static final String ACCOUNT_NUMBER_FORMAT = "Account number must have 6 to 10 digits";
    static final String ACCOUNT_TYPE_REQUIRED = "Account type is required";
    static final String INITIAL_BALANCE_REQUIRED = "Initial balance is required";
    static final String INITIAL_BALANCE_MIN = "Initial balance must be greater than or equal to 0";
    static final String INITIAL_BALANCE_DIGITS = "Initial balance allows at most 13 integer digits and 2 decimals";
    static final String CLIENT_ID_REQUIRED = "Client id is required";
    static final String ACTIVE_REQUIRED = "Active state is required";
    static final String AMOUNT_REQUIRED = "Movement amount is required";
    static final String AMOUNT_NON_ZERO = "Movement amount cannot be zero";
    static final String AMOUNT_RANGE = "Movement amount cannot exceed 1000000.00 in absolute value";
    static final String AMOUNT_DIGITS = "Movement amount allows at most 2 decimals";

    private AccountConstraints() {
    }
}
