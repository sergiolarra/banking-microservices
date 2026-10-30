package com.bank.accounts;

import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.model.AccountType;
import com.bank.accounts.domain.model.ClientSnapshot;
import com.bank.accounts.domain.port.AccountRepository;
import com.bank.accounts.infrastructure.messaging.ClientEventsTopology;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * F6-AC2: client events published on RabbitMQ 4 keep the local replica up to date,
 * exactly once per event id, tolerating out-of-order delivery (ADR-008, ADR-010).
 */
class ClientEventsIntegrationTest extends IntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AccountRepository accounts;

    @Test
    void createsTheReplicaFromClientCreated() {
        UUID clientId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        publish(event(eventId, "client.created", clientId, "Jose Lema", true, 0));

        ClientSnapshot snapshot = awaitSnapshot(clientId, 0);
        assertThat(snapshot.getName()).isEqualTo("Jose Lema");
        assertThat(snapshot.getIdentification()).isEqualTo("1712345678");
        assertThat(snapshot.canOperate()).isTrue();
        await().atMost(TIMEOUT).until(() -> processed(eventId));
    }

    /** The same event id is applied once, even if a redelivery carried different data. */
    @Test
    void appliesEachEventIdOnlyOnce() {
        UUID clientId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        publish(event(eventId, "client.created", clientId, "Jose Lema", true, 0));
        awaitSnapshot(clientId, 0);

        publish(event(eventId, "client.updated", clientId, "Should be ignored", false, 9));
        UUID marker = UUID.randomUUID();
        publish(event(marker, "client.updated", clientId, "Jose A. Lema", true, 1));

        ClientSnapshot snapshot = awaitSnapshot(clientId, 1);
        assertThat(snapshot.getName()).isEqualTo("Jose A. Lema");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = ?",
                Integer.class, eventId)).isEqualTo(1);
    }

    @Test
    void ignoresAnOlderEventThatArrivesLate() {
        UUID clientId = UUID.randomUUID();
        publish(event(UUID.randomUUID(), "client.updated", clientId, "Jose A. Lema", false, 2));
        awaitSnapshot(clientId, 2);

        UUID late = UUID.randomUUID();
        publish(event(late, "client.created", clientId, "Jose Lema", true, 0));
        await().atMost(TIMEOUT).until(() -> processed(late));

        ClientSnapshot snapshot = clients.findById(clientId).orElseThrow();
        assertThat(snapshot.getVersion()).isEqualTo(2);
        assertThat(snapshot.getName()).isEqualTo("Jose A. Lema");
        assertThat(snapshot.isActive()).isFalse();
    }

    @Test
    void adr016ClientDeletedDeactivatesItsAccounts() {
        UUID clientId = UUID.randomUUID();
        publish(event(UUID.randomUUID(), "client.created", clientId, "Jose Lema", true, 0));
        awaitSnapshot(clientId, 0);
        String accountNumber = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L));
        accounts.save(Account.open(accountNumber, AccountType.SAVINGS, BigDecimal.TEN, clientId, true));

        publish(event(UUID.randomUUID(), "client.deleted", clientId, "Jose Lema", true, 1));

        ClientSnapshot snapshot = awaitSnapshot(clientId, 1);
        assertThat(snapshot.isDeleted()).isTrue();
        assertThat(accounts.findByAccountNumber(accountNumber).orElseThrow().isActive()).isFalse();
    }

    @Test
    void adr010SendsAnUnreadableMessageToTheDeadLetterQueueAfterRetrying() {
        String marker = "not json " + UUID.randomUUID();

        publish(marker);

        await().atMost(Duration.ofSeconds(30)).until(() -> deadLettered(marker));
    }

    private boolean deadLettered(String body) {
        Message message = rabbitTemplate.receive(ClientEventsTopology.DEAD_LETTER_QUEUE, 500);
        return message != null && new String(message.getBody(), StandardCharsets.UTF_8).equals(body);
    }

    private boolean processed(UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class,
                eventId) == 1;
    }

    private ClientSnapshot awaitSnapshot(UUID clientId, long version) {
        return await().atMost(TIMEOUT).until(() -> clients.findById(clientId),
                snapshot -> snapshot.map(s -> s.getVersion() == version).orElse(false)).orElseThrow();
    }

    private void publish(String body) {
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        rabbitTemplate.send(ClientEventsTopology.EXCHANGE, "client.updated", message);
    }

    private static String event(UUID eventId, String type, UUID clientId, String name, boolean active, long version) {
        return """
                {"eventId":"%s","type":"%s","occurredAt":"2026-10-30T15:04:05Z","version":%d,
                 "correlationId":"it-events","data":{"clientId":"%s","name":"%s",
                 "identification":"1712345678","active":%s}}
                """.formatted(eventId, type, version, clientId, name, active);
    }
}
