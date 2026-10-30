package com.bank.clients;

import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.model.Gender;
import com.bank.clients.domain.model.PersonalDetails;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** Shared test data. */
public final class ClientFixtures {

    public static final String PASSWORD_HASH = "$2a$10$hashedPasswordValue";
    public static final Instant NOW = Instant.parse("2026-10-30T15:04:05Z");
    public static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private ClientFixtures() {
    }

    public static PersonalDetails details() {
        return new PersonalDetails("Jose Lema", Gender.MALE, 35, "1712345678", "Otavalo sn y principal", "098254785");
    }

    /** A client as returned by the repository: it already has an id and a version. */
    public static Client storedClient(long version) {
        return Client.restore(1L, UUID.fromString("3f6c2a8e-1b7d-4c1e-9a55-0c2f7e1d9b10"), version, details(),
                PASSWORD_HASH, true);
    }
}
