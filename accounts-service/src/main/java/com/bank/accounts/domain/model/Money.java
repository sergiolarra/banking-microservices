package com.bank.accounts.domain.model;

import com.bank.accounts.domain.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monetary rules shared by accounts and movements: two decimals and the limits of
 * {@code NUMERIC(15,2)} (ADR-021). Values are never rounded silently; a value with more
 * than two decimals is rejected.
 */
final class Money {

    /** Largest value that fits in {@code NUMERIC(15,2)}. */
    static final BigDecimal MAX_BALANCE = new BigDecimal("9999999999999.99");
    static final BigDecimal MAX_MOVEMENT_AMOUNT = new BigDecimal("1000000.00");

    private Money() {
    }

    /** Returns the value with scale 2, or fails if it is missing or has more than two decimals. */
    static BigDecimal twoDecimals(BigDecimal value, String field) {
        if (value == null) {
            throw new BusinessRuleException(field + " is required");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException(field + " allows at most 2 decimals");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    static BigDecimal requireWithinBalanceLimit(BigDecimal balance) {
        if (balance.compareTo(MAX_BALANCE) > 0) {
            throw new BusinessRuleException("The balance exceeds the maximum allowed");
        }
        return balance;
    }

    /** ACC-1 and the movement limits of SPEC §4.4. */
    static BigDecimal movementAmount(BigDecimal amount) {
        BigDecimal value = twoDecimals(amount, "Movement amount");
        if (value.signum() == 0) {
            throw new BusinessRuleException("Movement amount cannot be zero");
        }
        if (value.abs().compareTo(MAX_MOVEMENT_AMOUNT) > 0) {
            throw new BusinessRuleException("Movement amount cannot exceed 1000000.00 in absolute value");
        }
        return value;
    }
}
