package com.bank.accounts.domain.model;

import java.math.BigDecimal;

/** The sign of the amount decides the type (ADR-012). */
public enum MovementType {
    DEPOSIT,
    WITHDRAWAL;

    static MovementType of(BigDecimal amount) {
        if (amount.signum() == 0) {
            throw new IllegalArgumentException("A movement amount cannot be zero");
        }
        return amount.signum() > 0 ? DEPOSIT : WITHDRAWAL;
    }
}
