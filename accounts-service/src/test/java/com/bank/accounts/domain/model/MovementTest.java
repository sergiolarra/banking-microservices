package com.bank.accounts.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovementTest {

    private static final Instant NOW = Instant.parse("2026-10-30T15:04:05Z");

    @Test
    void derivesTheTypeFromTheSignWhenRestored() {
        assertThat(Movement.restore(1L, "478758", NOW, new BigDecimal("10.00"), BigDecimal.TEN).getMovementType())
                .isEqualTo(MovementType.DEPOSIT);
        assertThat(Movement.restore(2L, "478758", NOW, new BigDecimal("-10.00"), BigDecimal.ZERO).getMovementType())
                .isEqualTo(MovementType.WITHDRAWAL);
    }

    @Test
    void neverHasAZeroAmount() {
        assertThatThrownBy(() -> Movement.restore(1L, "478758", NOW, BigDecimal.ZERO, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresAnAccountAndADate() {
        assertThatThrownBy(() -> Movement.restore(1L, null, NOW, BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Movement.restore(1L, "478758", null, BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class);
    }
}
