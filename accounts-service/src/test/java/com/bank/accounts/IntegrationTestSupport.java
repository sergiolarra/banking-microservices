package com.bank.accounts;

import com.bank.accounts.domain.model.ClientSnapshot;
import com.bank.accounts.domain.port.ClientSnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup of the HTTP integration tests: one Spring context and one pair of
 * PostgreSQL 17 / RabbitMQ 4 containers for every subclass, plus helpers that create
 * data with unique keys so tests do not interfere in the shared database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected ClientSnapshotRepository clients;

    /** A client already replicated from clients-service. */
    protected UUID givenClient(boolean active) {
        UUID clientId = UUID.randomUUID();
        clients.save(ClientSnapshot.create(clientId, "Jose Lema", "1712345678", active, 0, Instant.now()));
        return clientId;
    }

    protected String openAccount(UUID clientId, String initialBalance) throws Exception {
        String accountNumber = newAccountNumber();
        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody(accountNumber, clientId, initialBalance)))
                .andExpect(status().isCreated());
        return accountNumber;
    }

    protected ResultActions registerMovement(String accountNumber, String amount) throws Exception {
        return mvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountNumber\":\"%s\",\"amount\":%s}".formatted(accountNumber, amount)));
    }

    protected BigDecimal balanceOf(String accountNumber) {
        return jdbc.queryForObject("SELECT available_balance FROM account WHERE account_number = ?",
                BigDecimal.class, accountNumber);
    }

    protected int movementCount(String accountNumber) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM movement m JOIN account a ON a.id = m.account_id
                WHERE a.account_number = ?""", Integer.class, accountNumber);
    }

    protected static String newAccountNumber() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
    }

    protected static String accountBody(String accountNumber, UUID clientId, String initialBalance) {
        return """
                {"accountNumber":"%s","accountType":"SAVINGS","initialBalance":%s,"clientId":"%s"}
                """.formatted(accountNumber, initialBalance, clientId);
    }
}
