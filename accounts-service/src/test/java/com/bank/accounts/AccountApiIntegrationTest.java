package com.bank.accounts;

import com.bank.accounts.domain.exception.DuplicateResourceException;
import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.model.AccountType;
import com.bank.accounts.domain.model.ClientSnapshot;
import com.bank.accounts.domain.model.Movement;
import com.bank.accounts.domain.port.AccountRepository;
import com.bank.accounts.domain.port.ClientSnapshotRepository;
import com.bank.accounts.domain.port.MovementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * accounts-service against real PostgreSQL 17: REST → use cases → JPA with the
 * pessimistic account lock (F1, F2, F3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountApiIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private ClientSnapshotRepository clients;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private MovementRepository movements;

    @Test
    void f2AndF3RegisterMovementsAndRejectAWithdrawalWithoutFunds() throws Exception {
        String accountNumber = openAccount(givenClient(true), "2000.00");

        registerMovement(accountNumber, "600")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movementType").value("DEPOSIT"))
                .andExpect(jsonPath("$.balance").value(2600.00));
        registerMovement(accountNumber, "-575")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movementType").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.balance").value(2025.00));

        registerMovement(accountNumber, "-2025.01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.detail").value("Insufficient funds"));

        assertThat(balanceOf(accountNumber)).isEqualByComparingTo("2025.00");
        assertThat(movementCount(accountNumber)).isEqualTo(2);
        mvc.perform(get("/movements").param("accountNumber", accountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(-575.00))
                .andExpect(jsonPath("$.content[1].amount").value(600.00));
        mvc.perform(get("/accounts/{n}", accountNumber))
                .andExpect(jsonPath("$.initialBalance").value(2000.00))
                .andExpect(jsonPath("$.availableBalance").value(2025.00));
    }

    @Test
    void f3Ac3AWithdrawalMayEmptyTheAccountButNotGoBelowZero() throws Exception {
        String accountNumber = openAccount(givenClient(true), "100.00");

        registerMovement(accountNumber, "-100").andExpect(status().isCreated()).andExpect(jsonPath("$.balance").value(0));
        registerMovement(accountNumber, "-0.01").andExpect(status().isUnprocessableContent());
    }

    @Test
    void f2Ac8CorrectsOnlyTheLatestMovement() throws Exception {
        String accountNumber = openAccount(givenClient(true), "2000.00");
        long deposit = idOf(registerMovement(accountNumber, "600"));
        long withdrawal = idOf(registerMovement(accountNumber, "-575"));

        mvc.perform(put("/movements/{id}", withdrawal).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(-100.00))
                .andExpect(jsonPath("$.balance").value(2500.00));
        mvc.perform(put("/movements/{id}", deposit).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOVEMENT_NOT_MODIFIABLE"));

        assertThat(balanceOf(accountNumber)).isEqualByComparingTo("2500.00");
        mvc.perform(get("/movements/{id}", withdrawal)).andExpect(jsonPath("$.balance").value(2500.00));
    }

    @Test
    void f2Ac7RejectsMovementsOnAnInactiveAccountOrForAnInactiveClient() throws Exception {
        UUID clientId = givenClient(true);
        String accountNumber = openAccount(clientId, "50.00");

        mvc.perform(patch("/accounts/{n}", accountNumber).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        registerMovement(accountNumber, "10")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INACTIVE_ACCOUNT"));

        mvc.perform(put("/accounts/{n}", accountNumber).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"CHECKING\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("CHECKING"));
        ClientSnapshot client = clients.findById(clientId).orElseThrow();
        client.update(client.getName(), client.getIdentification(), false, client.getVersion() + 1, Instant.now());
        clients.save(client);
        registerMovement(accountNumber, "10")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CLIENT_UNAVAILABLE"));
    }

    @Test
    void f1Ac6RejectsAnAccountForAnUnknownClient() throws Exception {
        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody(newAccountNumber(), UUID.randomUUID(), "1.00")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CLIENT_UNAVAILABLE"));
    }

    @Test
    void rejectsADuplicateAccountNumberAndListsByClient() throws Exception {
        UUID clientId = givenClient(true);
        String accountNumber = openAccount(clientId, "1.00");
        openAccount(clientId, "2.00");

        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody(accountNumber, clientId, "1.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
        mvc.perform(get("/accounts").param("clientId", clientId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/accounts").param("size", "1"))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/movements").param("size", "1")).andExpect(status().isOk());
    }

    /** The unique constraint is the last line of defense when two requests race. */
    @Test
    void repositoriesTranslateOrPropagateConstraintViolations() {
        UUID clientId = givenClient(true);
        String accountNumber = newAccountNumber();
        accounts.save(Account.open(accountNumber, AccountType.SAVINGS, BigDecimal.ONE, clientId, true));

        assertThatThrownBy(() -> accounts.save(
                Account.open(accountNumber, AccountType.SAVINGS, BigDecimal.ONE, clientId, true)))
                .isInstanceOf(DuplicateResourceException.class);
        assertThatThrownBy(() -> accounts.save(
                Account.open(newAccountNumber(), AccountType.SAVINGS, BigDecimal.ONE, UUID.randomUUID(), true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void repositoriesFailForRowsThatNoLongerExist() {
        UUID clientId = givenClient(true);
        Account vanished = Account.restore(Long.MAX_VALUE, newAccountNumber(), AccountType.SAVINGS, BigDecimal.ONE,
                BigDecimal.ONE, true, clientId);
        Movement orphan = Movement.restore(null, newAccountNumber(), Instant.now(), BigDecimal.ONE, BigDecimal.ONE);
        Movement vanishedMovement = Movement.restore(Long.MAX_VALUE, newAccountNumber(), Instant.now(),
                BigDecimal.ONE, BigDecimal.ONE);

        assertThatThrownBy(() -> accounts.save(vanished)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> movements.save(orphan)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> movements.save(vanishedMovement)).isInstanceOf(ResourceNotFoundException.class);
    }

    private UUID givenClient(boolean active) {
        UUID clientId = UUID.randomUUID();
        clients.save(ClientSnapshot.create(clientId, "Jose Lema", "1712345678", active, 0, Instant.now()));
        return clientId;
    }

    private String openAccount(UUID clientId, String initialBalance) throws Exception {
        String accountNumber = newAccountNumber();
        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody(accountNumber, clientId, initialBalance)))
                .andExpect(status().isCreated());
        return accountNumber;
    }

    private ResultActions registerMovement(String accountNumber, String amount) throws Exception {
        return mvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountNumber\":\"%s\",\"amount\":%s}".formatted(accountNumber, amount)));
    }

    private long idOf(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private BigDecimal balanceOf(String accountNumber) {
        return jdbc.queryForObject("SELECT available_balance FROM account WHERE account_number = ?",
                BigDecimal.class, accountNumber);
    }

    private int movementCount(String accountNumber) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM movement m JOIN account a ON a.id = m.account_id
                WHERE a.account_number = ?""", Integer.class, accountNumber);
    }

    private static String newAccountNumber() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
    }

    private static String accountBody(String accountNumber, UUID clientId, String initialBalance) {
        return """
                {"accountNumber":"%s","accountType":"SAVINGS","initialBalance":%s,"clientId":"%s"}
                """.formatted(accountNumber, initialBalance, clientId);
    }
}
