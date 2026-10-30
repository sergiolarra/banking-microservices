package com.bank.clients.infrastructure.rest.dto;

import com.bank.clients.domain.model.Gender;

import java.util.UUID;

/** Client representation; never includes the password (F1-AC1). */
public record ClientResponse(
        UUID clientId,
        String name,
        Gender gender,
        int age,
        String identification,
        String address,
        String phone,
        boolean active) {
}
