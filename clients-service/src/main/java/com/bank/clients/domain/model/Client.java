package com.bank.clients.domain.model;

import com.bank.clients.domain.exception.BusinessRuleException;

import java.util.Objects;
import java.util.UUID;

/**
 * A bank client (SPEC §4.2). Identity is the business key {@code clientId}; the
 * technical {@code id} is a persistence detail and is never exposed (ADR-007).
 *
 * <p>The client only knows the password hash: hashing happens behind the
 * {@code PasswordHasher} port (ADR-017).
 */
public final class Client extends Person {

    static final int MINIMUM_AGE = 18;

    private final UUID clientId;
    private final Long version;
    private String passwordHash;
    private boolean active;

    private Client(Long id, UUID clientId, Long version, PersonalDetails details, String passwordHash, boolean active) {
        super(id, requireAdult(details));
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.version = version;
        this.passwordHash = requirePasswordHash(passwordHash);
        this.active = active;
    }

    /** Registers a new client (CLI-2). A {@code null} {@code active} means active. */
    public static Client create(PersonalDetails details, String passwordHash, Boolean active) {
        return new Client(null, UUID.randomUUID(), null, details, passwordHash, active == null || active);
    }

    /**
     * Rebuilds a client that already exists, e.g. when loading it from persistence.
     * {@code version} increases with every stored change and orders the client events (ADR-010).
     */
    public static Client restore(
            Long id, UUID clientId, Long version, PersonalDetails details, String passwordHash, boolean active) {
        return new Client(id, clientId, version, details, passwordHash, active);
    }

    /** Applies new personal details with the same rules as {@link #create} (CLI-5). */
    public void updateDetails(PersonalDetails details) {
        applyDetails(requireAdult(details));
    }

    /** CLI-4. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = requirePasswordHash(newPasswordHash);
    }

    /** CLI-3: idempotent. */
    public void activate() {
        this.active = true;
    }

    /** CLI-3: idempotent. */
    public void deactivate() {
        this.active = false;
    }

    /** CLI-1. Age presence and range are validated by {@link Person}. */
    private static PersonalDetails requireAdult(PersonalDetails details) {
        if (details != null && details.age() != null && details.age() < MINIMUM_AGE) {
            throw new BusinessRuleException("El cliente debe ser mayor de edad");
        }
        return details;
    }

    private static String requirePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new BusinessRuleException("La contraseña es obligatoria");
        }
        return passwordHash;
    }

    public UUID getClientId() {
        return clientId;
    }

    /** {@code null} until the client is stored for the first time. */
    public Long getVersion() {
        return version;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Client client && clientId.equals(client.clientId);
    }

    @Override
    public int hashCode() {
        return clientId.hashCode();
    }

    /** Deliberately leaves out the password hash and personal data (principle 8). */
    @Override
    public String toString() {
        return "Client[clientId=" + clientId + ", active=" + active + "]";
    }
}
