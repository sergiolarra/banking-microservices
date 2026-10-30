package com.bank.accounts;

import com.bank.accounts.domain.exception.DuplicateResourceException;
import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.model.AccountType;
import com.bank.accounts.domain.model.Movement;
import com.bank.accounts.domain.port.AccountRepository;
import com.bank.accounts.domain.port.MovementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Accounts over HTTP and the persistence adapters against real PostgreSQL 17 (F1). */
class AccountApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AccountRepository accounts;
    @Autowired
    private MovementRepository movements;

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

    @Test
    void f1Ac7KeepsTheInitialBalanceWhenUpdating() throws Exception {
        String accountNumber = openAccount(givenClient(true), "75.00");

        mvc.perform(put("/accounts/{n}", accountNumber).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"CHECKING\",\"active\":true,\"initialBalance\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialBalance").value(75.00));
        assertThat(balanceOf(accountNumber)).isEqualByComparingTo("75.00");
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
}
