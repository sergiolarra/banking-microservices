package com.bank.accounts.infrastructure.rest.dto;

import com.bank.accounts.domain.model.MovementType;

import java.math.BigDecimal;
import java.time.Instant;

public record MovementResponse(
        Long id,
        Instant date,
        String accountNumber,
        MovementType movementType,
        BigDecimal amount,
        BigDecimal balance) {
}
