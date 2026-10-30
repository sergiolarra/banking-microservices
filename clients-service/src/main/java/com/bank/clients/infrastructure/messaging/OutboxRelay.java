package com.bank.clients.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

/**
 * Sends pending outbox events to RabbitMQ and marks them as published once the broker
 * confirms them. A failure leaves them pending for the next poll, so delivery is
 * at-least-once and consumers must be idempotent (ADR-009, ADR-010).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OutboxRelay {

    private final OutboxEventJpaRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    @Transactional
    public void relayPendingEvents() {
        List<OutboxEventEntity> pending = outbox.lockPending(properties.batchSize());
        if (pending.isEmpty()) {
            return;
        }
        try {
            rabbitTemplate.invoke(operations -> {
                pending.forEach(event -> operations.send(ClientEventsTopology.EXCHANGE, event.getType(), toMessage(event)));
                operations.waitForConfirmsOrDie(properties.confirmTimeout().toMillis());
                return null;
            });
        } catch (AmqpException e) {
            log.warn("Could not relay {} outbox events; they will be retried: {}", pending.size(), e.getMessage());
            return;
        }
        pending.forEach(event -> event.markPublished(clock.instant()));
        log.debug("Relayed {} outbox events", pending.size());
    }

    private static Message toMessage(OutboxEventEntity event) {
        return MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.getId().toString())
                .setType(event.getType())
                .build();
    }
}
