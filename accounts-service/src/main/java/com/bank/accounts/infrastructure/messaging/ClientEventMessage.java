package com.bank.accounts.infrastructure.messaging;

import com.bank.accounts.application.command.ClientEventCommand;
import com.bank.accounts.application.command.ClientEventType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/** Wire format of a client event (SPEC §6). Unknown fields are ignored (tolerant reader). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientEventMessage(
        UUID eventId,
        String type,
        Instant occurredAt,
        long version,
        String correlationId,
        Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(UUID clientId, String name, String identification, boolean active) {
    }

    ClientEventCommand toCommand() {
        return new ClientEventCommand(ClientEventType.fromEventName(type), data.clientId(), data.name(),
                data.identification(), data.active(), version, occurredAt);
    }
}
