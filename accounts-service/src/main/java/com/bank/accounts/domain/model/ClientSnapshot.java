package com.bank.accounts.domain.model;

import com.bank.accounts.domain.exception.ClientUnavailableException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Local, read-only copy of a client of clients-service (ADR-008). It changes only
 * through client events, and each event carries a version: older or repeated
 * versions are ignored, so events may arrive out of order (ADR-010).
 */
public final class ClientSnapshot {

    private final UUID clientId;
    private String name;
    private String identification;
    private boolean active;
    private boolean deleted;
    private long version;
    private Instant updatedAt;

    private ClientSnapshot(UUID clientId, String name, String identification, boolean active, boolean deleted,
                           long version, Instant updatedAt) {
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.name = name;
        this.identification = identification;
        this.active = active;
        this.deleted = deleted;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    /** First state known for a client, from whichever event arrives first. */
    public static ClientSnapshot create(UUID clientId, String name, String identification, boolean active,
                                        long version, Instant updatedAt) {
        return new ClientSnapshot(clientId, name, identification, active, false, version, updatedAt);
    }

    public static ClientSnapshot restore(UUID clientId, String name, String identification, boolean active,
                                         boolean deleted, long version, Instant updatedAt) {
        return new ClientSnapshot(clientId, name, identification, active, deleted, version, updatedAt);
    }

    /** Applies a newer state; returns {@code false} and changes nothing if the version is not newer. */
    public boolean update(String newName, String newIdentification, boolean newActive, long newVersion,
                          Instant at) {
        if (!isNewer(newVersion)) {
            return false;
        }
        this.name = newName;
        this.identification = newIdentification;
        this.active = newActive;
        this.version = newVersion;
        this.updatedAt = at;
        return true;
    }

    /** Marks the client as deleted; the data is kept for historical reports (ADR-016). */
    public boolean markDeleted(long newVersion, Instant at) {
        if (!isNewer(newVersion)) {
            return false;
        }
        this.deleted = true;
        this.active = false;
        this.version = newVersion;
        this.updatedAt = at;
        return true;
    }

    public boolean canOperate() {
        return active && !deleted;
    }

    /** F1-AC6, F2-AC7. */
    public void ensureCanOperate() {
        if (!canOperate()) {
            throw new ClientUnavailableException(
                    "El cliente %s está inactivo o fue eliminado".formatted(clientId));
        }
    }

    private boolean isNewer(long newVersion) {
        return newVersion > version;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getName() {
        return name;
    }

    public String getIdentification() {
        return identification;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
