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
            throw new BusinessRuleException(field + " es obligatorio");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException(field + " admite como máximo 2 decimales");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    static BigDecimal requireWithinBalanceLimit(BigDecimal balance) {
        if (balance.compareTo(MAX_BALANCE) > 0) {
            throw new BusinessRuleException("El saldo excede el máximo permitido");
        }
        return balance;
    }

    /** ACC-1 and the movement limits of SPEC §4.4. */
    static BigDecimal movementAmount(BigDecimal amount) {
        BigDecimal value = twoDecimals(amount, "El valor del movimiento");
        if (value.signum() == 0) {
            throw new BusinessRuleException("El valor del movimiento no puede ser cero");
        }
        if (value.abs().compareTo(MAX_MOVEMENT_AMOUNT) > 0) {
            throw new BusinessRuleException("El valor del movimiento no puede superar 1000000.00 en valor absoluto");
        }
        return value;
    }
}
