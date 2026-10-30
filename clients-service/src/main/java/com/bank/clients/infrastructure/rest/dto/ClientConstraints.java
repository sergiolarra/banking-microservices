package com.bank.clients.infrastructure.rest.dto;

/** Input rules shared by the client requests (SPEC §4.1, §4.2). Messages are user-facing (ADR-023). */
final class ClientConstraints {

    static final String IDENTIFICATION_REGEX = "^[A-Za-z0-9]{5,20}$";
    static final String PHONE_REGEX = "^\\+?[0-9]{7,15}$";
    static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";

    static final String NAME_REQUIRED = "El nombre es obligatorio";
    static final String NAME_SIZE = "El nombre debe tener entre 2 y 100 caracteres";
    static final String GENDER_REQUIRED = "El género es obligatorio";
    static final String AGE_REQUIRED = "La edad es obligatoria";
    static final String AGE_RANGE = "La edad debe estar entre 0 y 120";
    static final String IDENTIFICATION_REQUIRED = "La identificación es obligatoria";
    static final String IDENTIFICATION_FORMAT = "La identificación debe tener entre 5 y 20 caracteres alfanuméricos";
    static final String ADDRESS_REQUIRED = "La dirección es obligatoria";
    static final String ADDRESS_SIZE = "La dirección debe tener entre 5 y 200 caracteres";
    static final String PHONE_REQUIRED = "El teléfono es obligatorio";
    static final String PHONE_FORMAT = "El teléfono debe tener entre 7 y 15 dígitos, opcionalmente precedido de +";
    static final String PASSWORD_REQUIRED = "La contraseña es obligatoria";
    static final String PASSWORD_FORMAT =
            "La contraseña debe tener entre 8 y 64 caracteres, con al menos una letra y un dígito";
    static final String ACTIVE_REQUIRED = "El estado es obligatorio";

    private ClientConstraints() {
    }
}
