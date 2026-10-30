package com.bank.clients.infrastructure.messaging;

import com.bank.clients.domain.event.ClientEvent;

import java.time.Instant;
import java.util.UUID;

/** Wire format of a client event (SPEC §6). */
public record ClientEventMessage(
        UUID eventId,
        String type,
        Instant occurredAt,
        long version,
        String correlationId,
        Data data) {

    public record Data(UUID clientId, String name, String identification, boolean active) {
    }

    public static ClientEventMessage of(UUID eventId, ClientEvent event, String correlationId) {
        return new ClientEventMessage(eventId, event.type().eventName(), event.occurredAt(), event.version(),
                correlationId, new Data(event.clientId(), event.name(), event.identification(), event.active()));
    }
}
