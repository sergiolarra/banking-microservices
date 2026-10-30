package com.bank.accounts.infrastructure.rest;

import com.bank.accounts.application.usecase.GenerateAccountStatementUseCase;
import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.AccountStatement;
import com.bank.accounts.domain.model.DateRange;
import com.bank.accounts.domain.model.Movement;
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

import java.time.LocalDate;
import java.util.List;

import static com.bank.accounts.AccountFixtures.ACCOUNT_NUMBER;
import static com.bank.accounts.AccountFixtures.CLIENT_ID;
import static com.bank.accounts.AccountFixtures.NOW;
import static com.bank.accounts.AccountFixtures.activeClient;
import static com.bank.accounts.AccountFixtures.money;
import static com.bank.accounts.AccountFixtures.storedAccount;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of /reports (SPEC §5 F4). */
@WebMvcTest(ReportController.class)
@Import({ReportRestMapperImpl.class, ClockConfig.class})
class ReportControllerTest {

    private static final DateRange OCTOBER = new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GenerateAccountStatementUseCase generateAccountStatement;

    @Test
    void f4ReturnsTheAccountStatementAsJson() throws Exception {
        AccountStatement statement = AccountStatement.of(activeClient(), OCTOBER, NOW,
                List.of(storedAccount("1425.00")),
                List.of(Movement.restore(12L, ACCOUNT_NUMBER, NOW, money("-575.00"), money("1425.00"))));
        when(generateAccountStatement.execute(CLIENT_ID, OCTOBER)).thenReturn(statement);

        mvc.perform(get("/reports").param("date", "2026-10-01,2026-10-31").param("client", CLIENT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.client.clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.client.name").value("Jose Lema"))
                .andExpect(jsonPath("$.client.identification").value("1712345678"))
                .andExpect(jsonPath("$.range.from").value("2026-10-01"))
                .andExpect(jsonPath("$.range.to").value("2026-10-31"))
                .andExpect(jsonPath("$.generatedAt").value("2026-10-30T15:04:05Z"))
                .andExpect(jsonPath("$.accounts[0].accountNumber").value(ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.accounts[0].accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.accounts[0].active").value(true))
                .andExpect(jsonPath("$.accounts[0].initialBalance").value(2000.00))
                .andExpect(jsonPath("$.accounts[0].availableBalance").value(1425.00))
                .andExpect(jsonPath("$.accounts[0].totalCredits").value(0.00))
                .andExpect(jsonPath("$.accounts[0].totalDebits").value(575.00))
                .andExpect(jsonPath("$.accounts[0].movements[0].id").value(12))
                .andExpect(jsonPath("$.accounts[0].movements[0].date").value("2026-10-30T15:04:05Z"))
                .andExpect(jsonPath("$.accounts[0].movements[0].movementType").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.accounts[0].movements[0].amount").value(-575.00))
                .andExpect(jsonPath("$.accounts[0].movements[0].balance").value(1425.00));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2026-10-31,2026-10-01 | The start date cannot be after the end date",
            "2024-01-01,2025-01-01 | The date range cannot exceed 366 days",
            "2026-10-01            | date must have the form yyyy-MM-dd,yyyy-MM-dd"})
    void f4Ac4RejectsAnInvalidRange(String date, String detail) throws Exception {
        mvc.perform(get("/reports").param("date", date).param("client", CLIENT_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.title").value("Invalid parameter"))
                .andExpect(jsonPath("$.detail").value(detail));
        verifyNoInteractions(generateAccountStatement);
    }

    @Test
    void f4Ac4RequiresBothParameters() throws Exception {
        mvc.perform(get("/reports").param("client", CLIENT_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.detail").value("Required parameter 'date' is missing"));
        mvc.perform(get("/reports").param("date", "2026-10-01,2026-10-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Required parameter 'client' is missing"));
    }

    @Test
    void f4Ac4RejectsAClientThatIsNotAUuid() throws Exception {
        mvc.perform(get("/reports").param("date", "2026-10-01,2026-10-31").param("client", "jose"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void f4Ac5AnswersNotFoundForAnUnknownClient() throws Exception {
        when(generateAccountStatement.execute(CLIENT_ID, OCTOBER)).thenThrow(ResourceNotFoundException.client(CLIENT_ID));

        mvc.perform(get("/reports").param("date", "2026-10-01,2026-10-31").param("client", CLIENT_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
