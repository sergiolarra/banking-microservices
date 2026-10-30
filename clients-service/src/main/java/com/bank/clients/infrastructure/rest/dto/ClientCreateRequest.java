package com.bank.clients.infrastructure.rest.dto;

import com.bank.clients.domain.model.Gender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.bank.clients.infrastructure.rest.dto.ClientConstraints.*;

/** Body of {@code POST /clients}. Adulthood is a domain rule, so it answers 422 rather than 400 (F1-AC3). */
public record ClientCreateRequest(
        @NotBlank(message = NAME_REQUIRED) @Size(min = 2, max = 100, message = NAME_SIZE) String name,
        @NotNull(message = GENDER_REQUIRED) Gender gender,
        @NotNull(message = AGE_REQUIRED) @Min(value = 0, message = AGE_RANGE) @Max(value = 120, message = AGE_RANGE)
        Integer age,
        @NotBlank(message = IDENTIFICATION_REQUIRED) @Pattern(regexp = IDENTIFICATION_REGEX, message = IDENTIFICATION_FORMAT)
        String identification,
        @NotBlank(message = ADDRESS_REQUIRED) @Size(min = 5, max = 200, message = ADDRESS_SIZE) String address,
        @NotBlank(message = PHONE_REQUIRED) @Pattern(regexp = PHONE_REGEX, message = PHONE_FORMAT) String phone,
        @NotBlank(message = PASSWORD_REQUIRED) @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_FORMAT)
        String password,
        Boolean active) {
}
