package com.bank.accounts.infrastructure.rest.dto;

import com.bank.accounts.infrastructure.rest.validation.NonZero;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

import static com.bank.accounts.infrastructure.rest.dto.AccountConstraints.*;

/** Body of {@code POST /movements}: a positive amount deposits, a negative one withdraws (ADR-012, F2-AC6). */
public record MovementCreateRequest(
        @NotBlank(message = ACCOUNT_NUMBER_REQUIRED) @Pattern(regexp = ACCOUNT_NUMBER_REGEX, message = ACCOUNT_NUMBER_FORMAT)
        String accountNumber,
        @NotNull(message = AMOUNT_REQUIRED) @NonZero(message = AMOUNT_NON_ZERO)
        @DecimalMin(value = "-1000000.00", message = AMOUNT_RANGE) @DecimalMax(value = "1000000.00", message = AMOUNT_RANGE)
        @Digits(integer = 7, fraction = 2, message = AMOUNT_DIGITS) BigDecimal amount) {
}
