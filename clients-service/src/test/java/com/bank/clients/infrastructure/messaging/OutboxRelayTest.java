package com.bank.clients.infrastructure.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.bank.clients.ClientFixtures.FIXED_CLOCK;
import static com.bank.clients.ClientFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventJpaRepository outbox;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private RabbitOperations operations;

    private OutboxRelay relay;
    private OutboxEventEntity event;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outbox, rabbitTemplate, new OutboxProperties(100, Duration.ofSeconds(5)), FIXED_CLOCK);
        event = new OutboxEventEntity(UUID.randomUUID(), UUID.randomUUID(), "client.created", "{\"a\":1}", NOW);
    }

    @Test
    void doesNothingWhenThereAreNoPendingEvents() {
        when(outbox.lockPending(100)).thenReturn(List.of());

        relay.relayPendingEvents();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void sendsPendingEventsAndMarksThemPublishedOnceConfirmed() {
        when(outbox.lockPending(100)).thenReturn(List.of(event));
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation ->
                invocation.<RabbitTemplate.OperationsCallback<?>>getArgument(0).doInRabbit(operations));

        relay.relayPendingEvents();

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(operations).send(eq(ClientEventsTopology.EXCHANGE), eq("client.created"), message.capture());
        verify(operations).waitForConfirmsOrDie(5000);
        assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}");
        assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo(event.getId().toString());
        assertThat(message.getValue().getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void keepsEventsPendingWhenTheBrokerFails() {
        when(outbox.lockPending(100)).thenReturn(List.of(event));
        when(rabbitTemplate.invoke(any())).thenThrow(new AmqpConnectException(new ConnectException("down")));

        relay.relayPendingEvents();

        assertThat(event.getPublishedAt()).isNull();
    }
}
