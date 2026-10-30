package com.bank.accounts.domain.exception;

/**
 * Transport-agnostic classification of domain errors. The REST layer maps each
 * category to an HTTP status in a single place (ADR-026).
 */
public enum ErrorCategory {
    INVALID_INPUT,
    NOT_FOUND,
    CONFLICT,
    BUSINESS_RULE
}
