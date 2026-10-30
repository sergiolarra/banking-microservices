package com.bank.accounts.infrastructure.messaging;

import com.bank.accounts.application.usecase.ApplyClientEventUseCase;
import com.bank.accounts.infrastructure.observability.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Consumes client events (ADR-008). The event id is recorded in the same transaction
 * that updates the replica, so a redelivered event is applied only once (ADR-010).
 * Failures are retried by the container (1 s, 2 s, 4 s) and then dead-lettered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ClientEventListener {

    private final ApplyClientEventUseCase applyClientEvent;
    private final ProcessedEventJpaRepository processedEvents;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @RabbitListener(queues = ClientEventsTopology.QUEUE)
    @Transactional
    public void onClientEvent(Message message) {
        ClientEventMessage event = jsonMapper.readValue(message.getBody(), ClientEventMessage.class);
        if (event.correlationId() != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, event.correlationId());
        }
        try {
            if (processedEvents.existsById(event.eventId())) {
                log.info("Ignoring already processed event {} ({})", event.eventId(), event.type());
                return;
            }
            applyClientEvent.execute(event.toCommand());
            processedEvents.save(new ProcessedEventEntity(event.eventId(), clock.instant()));
            log.info("Applied event {} ({}) for client {}", event.eventId(), event.type(), event.data().clientId());
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
