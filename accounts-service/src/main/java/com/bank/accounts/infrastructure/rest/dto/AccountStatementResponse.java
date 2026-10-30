package com.bank.accounts.infrastructure.rest.dto;

import com.bank.accounts.domain.model.AccountType;
import com.bank.accounts.domain.model.MovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Body of {@code GET /reports} (SPEC §5 F4). */
public record AccountStatementResponse(
        Client client,
        Range range,
        Instant generatedAt,
        List<AccountEntry> accounts) {

    public record Client(UUID clientId, String name, String identification) {
    }

    public record Range(LocalDate from, LocalDate to) {
    }

    public record AccountEntry(
            String accountNumber,
            AccountType accountType,
            boolean active,
            BigDecimal initialBalance,
            BigDecimal availableBalance,
            BigDecimal totalCredits,
            BigDecimal totalDebits,
            List<MovementLine> movements) {
    }

    public record MovementLine(Long id, Instant date, MovementType movementType, BigDecimal amount,
                               BigDecimal balance) {
    }
}
