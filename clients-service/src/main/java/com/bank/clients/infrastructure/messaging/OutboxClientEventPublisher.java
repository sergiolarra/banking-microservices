package com.bank.clients.infrastructure.messaging;

import com.bank.clients.domain.event.ClientEvent;
import com.bank.clients.domain.port.ClientEventPublisher;
import com.bank.clients.infrastructure.observability.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Stores the event in the outbox instead of sending it. {@code MANDATORY} makes it
 * impossible to call outside the transaction that changes the client (ADR-009).
 */
@Component
@RequiredArgsConstructor
class OutboxClientEventPublisher implements ClientEventPublisher {

    private final OutboxEventJpaRepository outbox;
    private final JsonMapper jsonMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(ClientEvent event) {
        UUID eventId = UUID.randomUUID();
        ClientEventMessage message = ClientEventMessage.of(eventId, event, MDC.get(CorrelationIdFilter.MDC_KEY));
        outbox.save(new OutboxEventEntity(eventId, event.clientId(), message.type(),
                jsonMapper.writeValueAsString(message), event.occurredAt()));
    }
}
