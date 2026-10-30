package com.bank.accounts.application.command;

import com.bank.accounts.domain.model.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

/** Data to open an account. {@code active} may be {@code null} (defaults to active). */
public record CreateAccountCommand(
        String accountNumber, AccountType accountType, BigDecimal initialBalance, UUID clientId, Boolean active) {
}
