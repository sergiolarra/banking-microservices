package com.bank.clients.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param batchSize      events relayed per poll
 * @param confirmTimeout how long to wait for the broker to confirm a batch
 */
@ConfigurationProperties("app.outbox")
public record OutboxProperties(
        @DefaultValue("100") int batchSize,
        @DefaultValue("5s") Duration confirmTimeout) {
}
