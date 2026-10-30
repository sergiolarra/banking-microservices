package com.bank.accounts.infrastructure.rest;

import com.bank.accounts.application.command.AccountChanges;
import com.bank.accounts.application.command.CreateAccountCommand;
import com.bank.accounts.application.usecase.CorrectMovementUseCase;
import com.bank.accounts.application.usecase.CreateAccountUseCase;
import com.bank.accounts.application.usecase.GetAccountUseCase;
import com.bank.accounts.application.usecase.GetMovementUseCase;
import com.bank.accounts.application.usecase.ListAccountsUseCase;
import com.bank.accounts.application.usecase.ListMovementsUseCase;
import com.bank.accounts.application.usecase.RegisterMovementUseCase;
import com.bank.accounts.application.usecase.UpdateAccountUseCase;
import com.bank.accounts.domain.exception.ClientUnavailableException;
import com.bank.accounts.domain.exception.DuplicateResourceException;
import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.model.AccountType;
import com.bank.accounts.domain.pagination.PageQuery;
import com.bank.accounts.domain.pagination.PagedResult;
import com.bank.accounts.infrastructure.config.ClockConfig;
import com.bank.accounts.infrastructure.observability.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.bank.accounts.AccountFixtures.ACCOUNT_NUMBER;
import static com.bank.accounts.AccountFixtures.CLIENT_ID;
import static com.bank.accounts.AccountFixtures.money;
import static com.bank.accounts.AccountFixtures.storedAccount;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

/** HTTP contract of /accounts and the generic error handling (SPEC §5 F1, §8). */
@WebMvcTest({AccountController.class, MovementController.class})
@Import({AccountRestMapperImpl.class, ClockConfig.class})
class AccountControllerTest {

    private static final String CREATE_BODY = """
            {"accountNumber":"478758","accountType":"SAVINGS","initialBalance":2000.00,
             "clientId":"3f6c2a8e-1b7d-4c1e-9a55-0c2f7e1d9b10"}
            """;

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

    private final Account account = storedAccount("2000.00");

    @Test
    void opensAnAccount() throws Exception {
        when(createAccount.execute(new CreateAccountCommand(ACCOUNT_NUMBER, AccountType.SAVINGS, money("2000.00"),
                CLIENT_ID, null))).thenReturn(account);

        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/accounts/478758"))
                .andExpect(jsonPath("$.accountNumber").value("478758"))
                .andExpect(jsonPath("$.accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.initialBalance").value(2000.00))
                .andExpect(jsonPath("$.availableBalance").value(2000.00))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID.toString()));
    }

    @Test
    void rejectsInvalidAccountData() throws Exception {
        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"12A\",\"accountType\":\"SAVINGS\",\"initialBalance\":-1.005}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("accountNumber"))
                .andExpect(jsonPath("$.errors[0].message").value("Account number must have 6 to 10 digits"))
                .andExpect(jsonPath("$.errors[1].field").value("clientId"))
                .andExpect(jsonPath("$.errors[2].field").value("initialBalance"))
                .andExpect(jsonPath("$.errors[3].field").value("initialBalance"));
        verifyNoInteractions(createAccount);
    }

    @Test
    void f1Ac6AnswersUnprocessableForAnUnavailableClient() throws Exception {
        when(createAccount.execute(any())).thenThrow(ClientUnavailableException.unknown(CLIENT_ID));

        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CLIENT_UNAVAILABLE"))
                .andExpect(jsonPath("$.title").value("Client unavailable"));
    }

    @Test
    void answersConflictForADuplicateAccountNumber() throws Exception {
        when(createAccount.execute(any())).thenThrow(DuplicateResourceException.accountNumber(ACCOUNT_NUMBER));

        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void listsTheAccountsOfAClient() throws Exception {
        when(listAccounts.execute(CLIENT_ID, new PageQuery(0, 20)))
                .thenReturn(new PagedResult<>(List.of(account), 0, 20, 1));

        mvc.perform(get("/accounts").param("clientId", CLIENT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listsEveryAccountWithoutAFilter() throws Exception {
        when(listAccounts.execute(null, new PageQuery(2, 5))).thenReturn(new PagedResult<>(List.of(), 2, 5, 0));

        mvc.perform(get("/accounts").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2));
    }

    @Test
    void rejectsAClientIdThatIsNotAUuidAndAnInvalidPage() throws Exception {
        mvc.perform(get("/accounts").param("clientId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(get("/accounts").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("page must be greater than or equal to 0"));
    }

    @Test
    void getsAnAccount() throws Exception {
        when(getAccount.execute(ACCOUNT_NUMBER)).thenReturn(account);

        mvc.perform(get("/accounts/{n}", ACCOUNT_NUMBER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(ACCOUNT_NUMBER));
    }

    @Test
    void answersNotFoundForAnUnknownAccount() throws Exception {
        when(getAccount.execute("999999")).thenThrow(ResourceNotFoundException.account("999999"));

        mvc.perform(get("/accounts/{n}", "999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("No account exists with number 999999"));
    }

    @Test
    void f1Ac7PutOnlyChangesTypeAndStateEvenIfBalancesAreSent() throws Exception {
        AccountChanges expected = new AccountChanges(AccountType.CHECKING, false);
        when(updateAccount.execute(ACCOUNT_NUMBER, expected)).thenReturn(account);

        mvc.perform(put("/accounts/{n}", ACCOUNT_NUMBER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"CHECKING\",\"active\":false,\"initialBalance\":1,"
                                + "\"availableBalance\":999,\"clientId\":\"" + CLIENT_ID + "\"}"))
                .andExpect(status().isOk());
        verify(updateAccount).execute(ACCOUNT_NUMBER, expected);
    }

    @Test
    void putRequiresTypeAndState() throws Exception {
        mvc.perform(put("/accounts/{n}", ACCOUNT_NUMBER).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void patchSendsOnlyThePresentFields() throws Exception {
        AccountChanges expected = new AccountChanges(null, false);
        when(updateAccount.execute(ACCOUNT_NUMBER, expected)).thenReturn(account);

        mvc.perform(patch("/accounts/{n}", ACCOUNT_NUMBER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());
        verify(updateAccount).execute(ACCOUNT_NUMBER, expected);
    }

    @Test
    void f1Ac8AccountsCannotBeDeleted() throws Exception {
        mvc.perform(delete("/accounts/{n}", ACCOUNT_NUMBER))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.title").value("Method not allowed"));
    }

    @Test
    void rejectsMalformedJsonAndUnsupportedMediaTypes() throws Exception {
        mvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
        mvc.perform(post("/accounts").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void answersNotFoundForAnUnknownRouteAndNotAcceptableForXml() throws Exception {
        mvc.perform(get("/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        when(getAccount.execute(ACCOUNT_NUMBER)).thenReturn(account);
        mvc.perform(get("/accounts/{n}", ACCOUNT_NUMBER).accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void hidesTheDetailsOfUnexpectedErrors() throws Exception {
        when(getAccount.execute(ACCOUNT_NUMBER)).thenThrow(new IllegalStateException("database is on fire"));

        mvc.perform(get("/accounts/{n}", ACCOUNT_NUMBER))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."));
    }

    @Test
    void echoesOrReplacesTheCorrelationId() throws Exception {
        when(getAccount.execute(ACCOUNT_NUMBER)).thenReturn(account);

        mvc.perform(get("/accounts/{n}", ACCOUNT_NUMBER).header(CorrelationIdFilter.HEADER, "abc-123"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "abc-123"));
        mvc.perform(get("/accounts/{n}", ACCOUNT_NUMBER).header(CorrelationIdFilter.HEADER, "bad id!"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern("^[0-9a-f-]{36}$")));
    }
}
