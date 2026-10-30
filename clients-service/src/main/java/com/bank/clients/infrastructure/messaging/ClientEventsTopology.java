package com.bank.clients.infrastructure.messaging;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Broker resources owned by this service (SPEC §6). Queues belong to the consumers,
 * so only the exchange is declared here.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class ClientEventsTopology {

    public static final String EXCHANGE = "client.events";

    @Bean
    TopicExchange clientEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }
}
