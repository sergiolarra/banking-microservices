package com.bank.accounts.infrastructure.rest.dto;

import com.bank.accounts.infrastructure.rest.validation.NonZero;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

import static com.bank.accounts.infrastructure.rest.dto.AccountConstraints.*;

/** Body of {@code PUT /movements/{id}}: the new amount of the latest movement (F2-AC8). */
public record MovementCorrectionRequest(
        @NotNull(message = AMOUNT_REQUIRED) @NonZero(message = AMOUNT_NON_ZERO)
        @DecimalMin(value = "-1000000.00", message = AMOUNT_RANGE) @DecimalMax(value = "1000000.00", message = AMOUNT_RANGE)
        @Digits(integer = 7, fraction = 2, message = AMOUNT_DIGITS) BigDecimal amount) {
}
