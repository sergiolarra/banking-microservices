package com.bank.accounts;

import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.model.AccountType;
import com.bank.accounts.domain.model.ClientSnapshot;
import com.bank.accounts.domain.port.AccountRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F4 against real PostgreSQL 17: day boundaries in America/Guayaquil and query count. */
class AccountStatementIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AccountRepository accounts;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    /**
     * October 2026 in Guayaquil (UTC-5) runs from 2026-10-01T05:00Z to 2026-11-01T05:00Z.
     * The movements just outside those instants must not appear (F4-AC2).
     */
    @Test
    void f4Ac1To3ReturnsTheMovementsOfTheRangeOldestFirstWithTotals() throws Exception {
        UUID clientId = givenClient(true);
        String savings = givenAccount(clientId, "100000", "2000.00", "2025.00");
        String checking = givenAccount(clientId, "200000", "50.00", "50.00");
        insertMovement(savings, "2026-10-01T04:59:59.999Z", "1.00", "2001.00");
        insertMovement(savings, "2026-10-01T05:00:00Z", "600.00", "2600.00");
        insertMovement(savings, "2026-10-31T23:00:00Z", "-575.00", "2025.00");
        insertMovement(savings, "2026-11-01T05:00:00Z", "-1.00", "2024.00");

        statement(clientId, "2026-10-01,2026-10-31")
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.client.clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.accounts", hasSize(2)))
                .andExpect(jsonPath("$.accounts[0].accountNumber").value(savings))
                .andExpect(jsonPath("$.accounts[0].availableBalance").value(2025.00))
                .andExpect(jsonPath("$.accounts[0].totalCredits").value(600.00))
                .andExpect(jsonPath("$.accounts[0].totalDebits").value(575.00))
                .andExpect(jsonPath("$.accounts[0].movements", hasSize(2)))
                .andExpect(jsonPath("$.accounts[0].movements[0].amount").value(600.00))
                .andExpect(jsonPath("$.accounts[0].movements[1].amount").value(-575.00))
                .andExpect(jsonPath("$.accounts[1].accountNumber").value(checking))
                .andExpect(jsonPath("$.accounts[1].movements", hasSize(0)));
    }

    /** F4-AC6: the client, its accounts and their movements, whatever the number of accounts. */
    @Test
    void f4Ac6UsesAtMostThreeQueries() throws Exception {
        UUID clientId = givenClient(true);
        for (int i = 0; i < 5; i++) {
            String account = givenAccount(clientId, String.valueOf(300000 + i), "10.00", "11.00");
            insertMovement(account, "2026-10-15T12:00:00Z", "1.00", "11.00");
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        statement(clientId, "2026-10-01,2026-10-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts", hasSize(5)));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
        statistics.setStatisticsEnabled(false);
    }

    @Test
    void f4Ac3AClientWithoutAccountsGetsAnEmptyStatement() throws Exception {
        statement(givenClient(true), "2026-10-01,2026-10-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts", hasSize(0)));
    }

    @Test
    void f4Ac5ADeletedClientKeepsItsHistoryAndAnUnknownOneIsNotFound() throws Exception {
        UUID clientId = givenClient(true);
        String account = givenAccount(clientId, "400000", "10.00", "10.00");
        ClientSnapshot snapshot = clients.findById(clientId).orElseThrow();
        snapshot.markDeleted(1, Instant.now());
        clients.save(snapshot);

        statement(clientId, "2026-10-01,2026-10-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[0].accountNumber").value(account));
        statement(UUID.randomUUID(), "2026-10-01,2026-10-31")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private ResultActions statement(UUID clientId, String date) throws Exception {
        return mvc.perform(get("/reports").param("date", date).param("client", clientId.toString()));
    }

    /**
     * One random prefix per test keeps account numbers unique in the shared database,
     * while the suffix decides their order in the statement (accounts are listed by number).
     */
    private final String accountPrefix = String.valueOf(ThreadLocalRandom.current().nextInt(1000, 9999));

    private String givenAccount(UUID clientId, String suffix, String initial, String available) {
        String number = accountPrefix + suffix;
        Account account = accounts.save(Account.open(number, AccountType.SAVINGS, new BigDecimal(initial),
                clientId, true));
        jdbc.update("UPDATE account SET available_balance = ? WHERE id = ?", new BigDecimal(available),
                account.getId());
        return number;
    }

    private void insertMovement(String accountNumber, String date, String amount, String balance) {
        BigDecimal value = new BigDecimal(amount);
        jdbc.update("""
                INSERT INTO movement (account_id, movement_date, movement_type, amount, balance)
                SELECT id, ?, ?, ?, ? FROM account WHERE account_number = ?""",
                Timestamp.from(Instant.parse(date)), value.signum() > 0 ? "DEPOSIT" : "WITHDRAWAL", value,
                new BigDecimal(balance), accountNumber);
    }
}
