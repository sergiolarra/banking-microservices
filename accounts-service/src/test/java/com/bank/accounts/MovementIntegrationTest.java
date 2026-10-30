package com.bank.accounts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F6-AC1: movements over HTTP against real PostgreSQL 17, checking the final state in
 * the database, plus concurrency on one account (F2-AC5, ADR-013).
 */
class MovementIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void f6Ac1OpensDepositsWithdrawsAndRejectsAWithdrawalWithoutFunds() throws Exception {
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

    /**
     * F2-AC5: ten simultaneous withdrawals of 100.00 on 500.00. The account lock
     * serializes them, so exactly five succeed and the balance never goes below zero.
     */
    @Test
    void f2Ac5ConcurrentWithdrawalsNeverOverdrawTheAccount() throws Exception {
        String accountNumber = openAccount(givenClient(true), "500.00");
        int requests = 10;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> withdrawals = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            withdrawals.add(() -> {
                start.await();
                return registerMovement(accountNumber, "-100.00").andReturn().getResponse().getStatus();
            });
        }

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            List<Future<Integer>> results = withdrawals.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
        }

        Map<Integer, Long> byStatus = statuses.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(byStatus).containsOnly(Map.entry(201, 5L), Map.entry(422, 5L));
        assertThat(balanceOf(accountNumber)).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForList("""
                SELECT m.balance FROM movement m JOIN account a ON a.id = m.account_id
                WHERE a.account_number = ? ORDER BY m.balance DESC""", BigDecimal.class, accountNumber))
                .extracting(BigDecimal::toPlainString)
                .containsExactly("400.00", "300.00", "200.00", "100.00", "0.00");
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
        var client = clients.findById(clientId).orElseThrow();
        client.update(client.getName(), client.getIdentification(), false, client.getVersion() + 1, Instant.now());
        clients.save(client);
        registerMovement(accountNumber, "10")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CLIENT_UNAVAILABLE"));
        assertThat(movementCount(accountNumber)).isZero();
    }

    private long idOf(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
