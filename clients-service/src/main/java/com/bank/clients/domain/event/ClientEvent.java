package com.bank.clients.domain.event;

import com.bank.clients.domain.model.Client;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Something that happened to a client, with the data other services may replicate
 * (SPEC §6). It never carries the password hash (ADR-017).
 *
 * @param version orders the events of one client; consumers ignore versions they
 *                have already applied (ADR-010)
 */
public record ClientEvent(
        ClientEventType type,
        UUID clientId,
        String name,
        String identification,
        boolean active,
        long version,
        Instant occurredAt) {

    public static ClientEvent created(Client client, Instant occurredAt) {
        return of(ClientEventType.CREATED, client, storedVersion(client), occurredAt);
    }

    public static ClientEvent updated(Client client, Instant occurredAt) {
        return of(ClientEventType.UPDATED, client, storedVersion(client), occurredAt);
    }

    /** A deletion is one more change, so it gets the next version. */
    public static ClientEvent deleted(Client client, Instant occurredAt) {
        return of(ClientEventType.DELETED, client, storedVersion(client) + 1, occurredAt);
    }

    private static ClientEvent of(ClientEventType type, Client client, long version, Instant occurredAt) {
        return new ClientEvent(type, client.getClientId(), client.getName(), client.getIdentification(),
                client.isActive(), version, occurredAt);
    }

    private static long storedVersion(Client client) {
        return Objects.requireNonNull(client.getVersion(), "client must be stored before publishing events");
    }
}
