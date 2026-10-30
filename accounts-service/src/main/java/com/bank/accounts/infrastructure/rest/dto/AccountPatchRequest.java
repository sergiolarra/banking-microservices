package com.bank.accounts.infrastructure.rest.dto;

import com.bank.accounts.domain.model.AccountType;

/** Body of {@code PATCH /accounts/{accountNumber}}: only the fields present change. */
public record AccountPatchRequest(AccountType accountType, Boolean active) {
}
