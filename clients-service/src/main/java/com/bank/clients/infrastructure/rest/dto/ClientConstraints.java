package com.bank.clients.infrastructure.rest.dto;

/** Input rules shared by the client requests (SPEC §4.1, §4.2). Messages are in English (ADR-027). */
final class ClientConstraints {

    static final String IDENTIFICATION_REGEX = "^[A-Za-z0-9]{5,20}$";
    static final String PHONE_REGEX = "^\\+?[0-9]{7,15}$";
    static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";

    static final String NAME_REQUIRED = "Name is required";
    static final String NAME_SIZE = "Name must be between 2 and 100 characters long";
    static final String GENDER_REQUIRED = "Gender is required";
    static final String AGE_REQUIRED = "Age is required";
    static final String AGE_RANGE = "Age must be between 0 and 120";
    static final String IDENTIFICATION_REQUIRED = "Identification is required";
    static final String IDENTIFICATION_FORMAT = "Identification must be 5 to 20 alphanumeric characters";
    static final String ADDRESS_REQUIRED = "Address is required";
    static final String ADDRESS_SIZE = "Address must be between 5 and 200 characters long";
    static final String PHONE_REQUIRED = "Phone is required";
    static final String PHONE_FORMAT = "Phone must have 7 to 15 digits, optionally preceded by +";
    static final String PASSWORD_REQUIRED = "Password is required";
    static final String PASSWORD_FORMAT =
            "Password must be 8 to 64 characters long and contain at least one letter and one digit";
    static final String ACTIVE_REQUIRED = "Active state is required";

    private ClientConstraints() {
    }
}
