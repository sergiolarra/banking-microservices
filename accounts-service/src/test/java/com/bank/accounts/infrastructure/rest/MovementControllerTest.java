package com.bank.accounts.infrastructure.rest;

import com.bank.accounts.application.usecase.CorrectMovementUseCase;
import com.bank.accounts.application.usecase.CreateAccountUseCase;
import com.bank.accounts.application.usecase.GetAccountUseCase;
import com.bank.accounts.application.usecase.GetMovementUseCase;
import com.bank.accounts.application.usecase.ListAccountsUseCase;
import com.bank.accounts.application.usecase.ListMovementsUseCase;
import com.bank.accounts.application.usecase.RegisterMovementUseCase;
import com.bank.accounts.application.usecase.UpdateAccountUseCase;
import com.bank.accounts.domain.exception.ClientUnavailableException;
import com.bank.accounts.domain.exception.InactiveAccountException;
import com.bank.accounts.domain.exception.InsufficientFundsException;
import com.bank.accounts.domain.exception.MovementNotModifiableException;
import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.Movement;
import com.bank.accounts.domain.pagination.PageQuery;
import com.bank.accounts.domain.pagination.PagedResult;
import com.bank.accounts.infrastructure.config.ClockConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.bank.accounts.AccountFixtures.ACCOUNT_NUMBER;
import static com.bank.accounts.AccountFixtures.CLIENT_ID;
import static com.bank.accounts.AccountFixtures.NOW;
import static com.bank.accounts.AccountFixtures.money;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of /movements (SPEC §5 F1–F3). */
@WebMvcTest({AccountController.class, MovementController.class})
@Import({AccountRestMapperImpl.class, ClockConfig.class})
class MovementControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreateAccountUseCase createAccount;
    @MockitoBean
    private GetAccountUseCase getAccount;
    @MockitoBean
    private ListAccountsUseCase listAccounts;
    @MockitoBean
    private UpdateAccountUseCase updateAccount;
    @MockitoBean
    private RegisterMovementUseCase registerMovement;
    @MockitoBean
    private GetMovementUseCase getMovement;
    @MockitoBean
    private ListMovementsUseCase listMovements;
    @MockitoBean
    private CorrectMovementUseCase correctMovement;

    private static String body(String amount) {
        return "{\"accountNumber\":\"478758\",\"amount\":" + amount + "}";
    }

    private static Movement movement(long id, String amount, String balance) {
        return Movement.restore(id, ACCOUNT_NUMBER, NOW, money(amount), money(balance));
    }

    @Test
    void f2Ac1RegistersADeposit() throws Exception {
        when(registerMovement.execute(ACCOUNT_NUMBER, money("600.00"))).thenReturn(movement(12, "600.00", "2600.00"));

        mvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON).content(body("600.00")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/movements/12"))
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.date").value("2026-10-30T15:04:05Z"))
                .andExpect(jsonPath("$.accountNumber").value(ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.movementType").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(600.00))
                .andExpect(jsonPath("$.balance").value(2600.00));
    }

    @Test
    void f2Ac2RegistersAWithdrawal() throws Exception {
        when(registerMovement.execute(ACCOUNT_NUMBER, money("-575.00")))
                .thenReturn(movement(13, "-575.00", "1425.00"));

        mvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON).content(body("-575.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movementType").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.balance").value(1425.00));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0        | Movement amount cannot be zero",
            "0.00     | Movement amount cannot be zero",
            "10.555   | Movement amount allows at most 2 decimals",
            "-1000000.01 | Movement amount cannot exceed 1000000.00 in absolute value",
            "1000000.01  | Movement amount cannot exceed 1000000.00 in absolute value",
            "null     | Movement amount is required"})
    void f2Ac6RejectsInvalidAmounts(String amount, String message) throws Exception {
        mvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON).content(body(amount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.message == '%s')]".formatted(message)).exists());
        verifyNoInteractions(registerMovement);
    }

    @Test
    void f3Ac1AnswersInsufficientFundsWithTheAgreedBody() throws Exception {
        when(registerMovement.execute(ACCOUNT_NUMBER, money("-150.00"))).thenThrow(new InsufficientFundsException());

        mvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON).content(body("-150.00")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://bank.local/errors/insufficient-funds"))
                .andExpect(jsonPath("$.title").value("Insufficient funds"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Insufficient funds"))
                .andExpect(jsonPath("$.instance").value("/movements"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void f2Ac7AnswersUnprocessableForAnInactiveAccountOrClient() throws Exception {
        when(registerMovement.execute(eq(ACCOUNT_NUMBER), any()))
                .thenThrow(new InactiveAccountException("Inactive account"))
                .thenThrow(ClientUnavailableException.inactiveOrDeleted(CLIENT_ID));

        mvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON).content(body("10")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INACTIVE_ACCOUNT"));
        mvc.perform(post("/movements").contentType(MediaType.APPLICATION_JSON).content(body("10")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CLIENT_UNAVAILABLE"));
    }

    @Test
    void f2Ac3ListsTheMovementsOfAnAccount() throws Exception {
        when(listMovements.execute(ACCOUNT_NUMBER, new PageQuery(0, 20)))
                .thenReturn(new PagedResult<>(List.of(movement(13, "-575.00", "1425.00"),
                        movement(12, "600.00", "2600.00")), 0, 20, 2));

        mvc.perform(get("/movements").param("accountNumber", ACCOUNT_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(13))
                .andExpect(jsonPath("$.content[1].id").value(12))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getsOneMovement() throws Exception {
        when(getMovement.execute(12)).thenReturn(movement(12, "600.00", "2600.00"));

        mvc.perform(get("/movements/{id}", 12)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(12));
    }

    @Test
    void answersNotFoundOrBadRequestForUnknownOrInvalidIds() throws Exception {
        when(getMovement.execute(404)).thenThrow(ResourceNotFoundException.movement(404));

        mvc.perform(get("/movements/{id}", 404)).andExpect(status().isNotFound());
        mvc.perform(get("/movements/{id}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Parameter 'id' has an invalid value"));
    }

    @Test
    void f2Ac8CorrectsTheLatestMovement() throws Exception {
        when(correctMovement.execute(13, money("-100.00"))).thenReturn(movement(13, "-100.00", "1900.00"));

        mvc.perform(put("/movements/{id}", 13).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":-100.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1900.00));
    }

    @Test
    void f2Ac8AnswersConflictForAnOlderMovement() throws Exception {
        when(correctMovement.execute(eq(12L), any()))
                .thenThrow(new MovementNotModifiableException("Only the latest movement of the account can be modified"));

        mvc.perform(put("/movements/{id}", 12).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOVEMENT_NOT_MODIFIABLE"));
    }

    @Test
    void correctionValidatesTheAmount() throws Exception {
        mvc.perform(put("/movements/{id}", 13).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Movement amount cannot be zero"));
    }

    @Test
    void f1Ac8MovementsCannotBePatchedOrDeleted() throws Exception {
        mvc.perform(delete("/movements/{id}", 12)).andExpect(status().isMethodNotAllowed());
        mvc.perform(patch("/movements/{id}", 12).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
