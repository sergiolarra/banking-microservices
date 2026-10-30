package com.bank.clients.infrastructure.rest.dto;

import com.bank.clients.domain.model.Gender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.bank.clients.infrastructure.rest.dto.ClientConstraints.*;

/** Body of {@code PUT /clients/{clientId}}: replaces every field; the password is optional. */
public record ClientUpdateRequest(
        @NotBlank(message = NAME_REQUIRED) @Size(min = 2, max = 100, message = NAME_SIZE) String name,
        @NotNull(message = GENDER_REQUIRED) Gender gender,
        @NotNull(message = AGE_REQUIRED) @Min(value = 0, message = AGE_RANGE) @Max(value = 120, message = AGE_RANGE)
        Integer age,
        @NotBlank(message = IDENTIFICATION_REQUIRED) @Pattern(regexp = IDENTIFICATION_REGEX, message = IDENTIFICATION_FORMAT)
        String identification,
        @NotBlank(message = ADDRESS_REQUIRED) @Size(min = 5, max = 200, message = ADDRESS_SIZE) String address,
        @NotBlank(message = PHONE_REQUIRED) @Pattern(regexp = PHONE_REGEX, message = PHONE_FORMAT) String phone,
        @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_FORMAT) String password,
        @NotNull(message = ACTIVE_REQUIRED) Boolean active) {
}
