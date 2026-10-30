package com.bank.accounts.application.command;

import java.time.Instant;
import java.util.UUID;

/** A client event received from clients-service, already parsed. */
public record ClientEventCommand(
        ClientEventType type,
        UUID clientId,
        String name,
        String identification,
        boolean active,
        long version,
        Instant occurredAt) {
}
