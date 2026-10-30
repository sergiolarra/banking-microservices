package com.bank.accounts.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Broker resources this service consumes from (SPEC §6). Queues are durable because
 * RabbitMQ 4 no longer accepts transient non-exclusive queues. A message that keeps
 * failing after the retries is dead-lettered to the DLQ (ADR-010).
 */
@Configuration(proxyBeanMethods = false)
public class ClientEventsTopology {

    public static final String EXCHANGE = "client.events";
    public static final String QUEUE = "accounts-service.client-events";
    public static final String DEAD_LETTER_EXCHANGE = "client.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "accounts-service.client-events.dlq";

    /** Declared by both services; RabbitMQ accepts identical declarations. */
    @Bean
    TopicExchange clientEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    Queue clientEventsQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    Binding clientEventsBinding() {
        return BindingBuilder.bind(clientEventsQueue()).to(clientEventsExchange()).with("client.*");
    }

    @Bean
    DirectExchange clientEventsDeadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue clientEventsDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding clientEventsDeadLetterBinding() {
        return BindingBuilder.bind(clientEventsDeadLetterQueue()).to(clientEventsDeadLetterExchange())
                .with(DEAD_LETTER_QUEUE);
    }
}
