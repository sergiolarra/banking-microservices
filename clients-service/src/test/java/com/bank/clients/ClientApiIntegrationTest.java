package com.bank.clients;

import com.bank.clients.domain.exception.DuplicateResourceException;
import com.bank.clients.domain.exception.ResourceNotFoundException;
import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.model.Gender;
import com.bank.clients.domain.model.PersonalDetails;
import com.bank.clients.domain.port.ClientRepository;
import com.bank.clients.infrastructure.messaging.ClientEventsTopology;
import com.bank.clients.infrastructure.observability.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.bank.clients.ClientFixtures.PASSWORD_HASH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check of clients-service against real PostgreSQL 17 and RabbitMQ:
 * REST → use cases → JPA → outbox → relay → broker (F1, ADR-009).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ClientApiIntegrationTest {

    private static final long RECEIVE_TIMEOUT_MS = 10_000;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AmqpAdmin amqpAdmin;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private ClientRepository clientRepository;

    private String eventsQueue;

    /**
     * Binds a private queue before each test, so it receives every event the test triggers.
     * It is not auto-delete, because each timed receive cancels its consumer, and it is
     * durable, because RabbitMQ 4 no longer allows transient non-exclusive queues.
     */
    @BeforeEach
    void bindEventsQueue() {
        eventsQueue = "it.client-events." + UUID.randomUUID();
        amqpAdmin.declareQueue(new Queue(eventsQueue, true, false, false));
        amqpAdmin.declareBinding(new Binding(eventsQueue, Binding.DestinationType.QUEUE,
                ClientEventsTopology.EXCHANGE, "client.*", null));
    }

    @AfterEach
    void deleteEventsQueue() {
        amqpAdmin.deleteQueue(eventsQueue);
    }

    @Test
    void f1ManagesTheWholeClientLifecycleAndPublishesItsEvents() throws Exception {
        String identification = newIdentification();

        String created = mvc.perform(post("/clients").header(CorrelationIdFilter.HEADER, "it-lifecycle")
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(identification)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID clientId = UUID.fromString(jsonMapper.readTree(created).get("clientId").asString());

        String storedPassword = jdbc.queryForObject("SELECT password FROM client WHERE client_id = ?",
                String.class, clientId);
        assertThat(storedPassword).startsWith("$2a$").isNotEqualTo("Clave1234");

        mvc.perform(patch("/clients/{id}", clientId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.name").value("Jose Lema"));

        mvc.perform(put("/clients/{id}", clientId).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(identification).replace("Jose Lema", "Jose A. Lema")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jose A. Lema"));

        mvc.perform(delete("/clients/{id}", clientId)).andExpect(status().isNoContent());
        mvc.perform(get("/clients/{id}", clientId)).andExpect(status().isNotFound());

        List<JsonNode> events = receiveEventsOf(clientId, 4);
        assertThat(events).extracting(event -> event.get("type").asString())
                .containsExactly("client.created", "client.updated", "client.updated", "client.deleted");
        assertThat(events).extracting(event -> event.get("version").asLong()).containsExactly(0L, 1L, 2L, 3L);
        assertThat(events.get(0).get("correlationId").asString()).isEqualTo("it-lifecycle");
        assertThat(events.get(1).get("data").get("active").asBoolean()).isFalse();
        assertThat(events.get(2).get("data").get("name").asString()).isEqualTo("Jose A. Lema");
        assertThat(events.get(0).get("data").has("password")).isFalse();

        Integer pending = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE aggregate_id = ? AND published_at IS NULL",
                Integer.class, clientId);
        assertThat(pending).isZero();
    }

    @Test
    void f1Ac2RejectsADuplicateIdentification() throws Exception {
        String identification = newIdentification();
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(createBody(identification)))
                .andExpect(status().isCreated());

        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(createBody(identification)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void listsTheStoredClients() throws Exception {
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(createBody(newIdentification())))
                .andExpect(status().isCreated());

        mvc.perform(get("/clients").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    /** Covers the race the use case cannot see: the unique constraint is the last line of defense. */
    @Test
    void translatesAnIdentificationConstraintViolationIntoADomainError() {
        String identification = newIdentification();
        clientRepository.save(Client.create(details(identification), PASSWORD_HASH, true));

        assertThatThrownBy(() -> clientRepository.save(Client.create(details(identification), PASSWORD_HASH, true)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void propagatesOtherConstraintViolations() {
        Client stored = clientRepository.save(Client.create(details(newIdentification()), PASSWORD_HASH, true));
        Client sameClientId = Client.restore(null, stored.getClientId(), null, details(newIdentification()),
                PASSWORD_HASH, true);

        assertThatThrownBy(() -> clientRepository.save(sameClientId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void failsToSaveAClientThatNoLongerExists() {
        Client vanished = Client.restore(Long.MAX_VALUE, UUID.randomUUID(), 0L, details(newIdentification()),
                PASSWORD_HASH, true);

        assertThatThrownBy(() -> clientRepository.save(vanished)).isInstanceOf(ResourceNotFoundException.class);
    }

    private List<JsonNode> receiveEventsOf(UUID clientId, int expected) {
        List<JsonNode> events = new ArrayList<>();
        while (events.size() < expected) {
            Message message = rabbitTemplate.receive(eventsQueue, RECEIVE_TIMEOUT_MS);
            assertThat(message).as("event %d of %d for %s", events.size() + 1, expected, clientId).isNotNull();
            JsonNode event = jsonMapper.readTree(message.getBody());
            if (event.get("data").get("clientId").asString().equals(clientId.toString())) {
                events.add(event);
            }
        }
        return events;
    }

    private static String newIdentification() {
        return "IT" + ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
    }

    private static PersonalDetails details(String identification) {
        return new PersonalDetails("Jose Lema", Gender.MALE, 35, identification, "Otavalo sn y principal", "098254785");
    }

    private static String createBody(String identification) {
        return """
                {"name":"Jose Lema","gender":"MALE","age":35,"identification":"%s",
                 "address":"Otavalo sn y principal","phone":"098254785","password":"Clave1234","active":true}
                """.formatted(identification);
    }
}
