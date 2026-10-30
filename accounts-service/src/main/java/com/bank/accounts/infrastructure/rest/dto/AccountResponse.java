package com.bank.accounts.infrastructure.rest.dto;

import com.bank.accounts.domain.model.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
        String accountNumber,
        AccountType accountType,
        BigDecimal initialBalance,
        BigDecimal availableBalance,
        boolean active,
        UUID clientId) {
}
