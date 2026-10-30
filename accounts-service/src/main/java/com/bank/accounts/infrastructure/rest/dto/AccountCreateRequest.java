package com.bank.accounts.infrastructure.rest.dto;

import com.bank.accounts.domain.model.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

import static com.bank.accounts.infrastructure.rest.dto.AccountConstraints.*;

/** Body of {@code POST /accounts}. */
public record AccountCreateRequest(
        @NotBlank(message = ACCOUNT_NUMBER_REQUIRED) @Pattern(regexp = ACCOUNT_NUMBER_REGEX, message = ACCOUNT_NUMBER_FORMAT)
        String accountNumber,
        @NotNull(message = ACCOUNT_TYPE_REQUIRED) AccountType accountType,
        @NotNull(message = INITIAL_BALANCE_REQUIRED) @DecimalMin(value = "0.00", message = INITIAL_BALANCE_MIN)
        @Digits(integer = 13, fraction = 2, message = INITIAL_BALANCE_DIGITS) BigDecimal initialBalance,
        @NotNull(message = CLIENT_ID_REQUIRED) UUID clientId,
        Boolean active) {
}
