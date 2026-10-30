package com.bank.clients.infrastructure.rest.dto;

import com.bank.clients.domain.model.Gender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.bank.clients.infrastructure.rest.dto.ClientConstraints.*;

/** Body of {@code PATCH /clientes/{clientId}}: only the fields present are validated and changed. */
public record ClientPatchRequest(
        @Size(min = 2, max = 100, message = NAME_SIZE) String name,
        Gender gender,
        @Min(value = 0, message = AGE_RANGE) @Max(value = 120, message = AGE_RANGE) Integer age,
        @Pattern(regexp = IDENTIFICATION_REGEX, message = IDENTIFICATION_FORMAT) String identification,
        @Size(min = 5, max = 200, message = ADDRESS_SIZE) String address,
        @Pattern(regexp = PHONE_REGEX, message = PHONE_FORMAT) String phone,
        @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_FORMAT) String password,
        Boolean active) {
}
