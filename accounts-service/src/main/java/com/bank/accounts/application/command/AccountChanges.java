package com.bank.accounts.application.command;

import com.bank.accounts.domain.model.AccountType;

/**
 * Changes to an account. Only the type and the state can change (F1-AC7); a
 * {@code null} field keeps the current value, which serves both PUT and PATCH.
 */
public record AccountChanges(AccountType accountType, Boolean active) {
}
