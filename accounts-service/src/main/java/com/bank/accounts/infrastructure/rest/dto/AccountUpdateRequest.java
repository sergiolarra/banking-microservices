package com.bank.accounts.infrastructure.rest.dto;

import com.bank.accounts.domain.model.AccountType;
import jakarta.validation.constraints.NotNull;

import static com.bank.accounts.infrastructure.rest.dto.AccountConstraints.ACCOUNT_TYPE_REQUIRED;
import static com.bank.accounts.infrastructure.rest.dto.AccountConstraints.ACTIVE_REQUIRED;

/** Body of {@code PUT /accounts/{accountNumber}}: only type and state are modifiable (F1-AC7). */
public record AccountUpdateRequest(
        @NotNull(message = ACCOUNT_TYPE_REQUIRED) AccountType accountType,
        @NotNull(message = ACTIVE_REQUIRED) Boolean active) {
}
