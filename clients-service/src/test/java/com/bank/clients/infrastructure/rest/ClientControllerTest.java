package com.bank.clients.infrastructure.rest;

import com.bank.clients.application.command.ClientChanges;
import com.bank.clients.application.command.CreateClientCommand;
import com.bank.clients.application.usecase.CreateClientUseCase;
import com.bank.clients.application.usecase.DeleteClientUseCase;
import com.bank.clients.application.usecase.GetClientUseCase;
import com.bank.clients.application.usecase.ListClientsUseCase;
import com.bank.clients.application.usecase.UpdateClientUseCase;
import com.bank.clients.domain.exception.BusinessRuleException;
import com.bank.clients.domain.exception.DuplicateResourceException;
import com.bank.clients.domain.exception.ResourceNotFoundException;
import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.pagination.PageQuery;
import com.bank.clients.domain.pagination.PagedResult;
import com.bank.clients.infrastructure.config.ClockConfig;
import com.bank.clients.infrastructure.observability.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.bank.clients.ClientFixtures.details;
import static com.bank.clients.ClientFixtures.storedClient;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

/** HTTP contract of /clients and the error catalog (SPEC §5 F1, §8). */
@WebMvcTest(ClientController.class)
@Import({ClientRestMapperImpl.class, ClockConfig.class})
class ClientControllerTest {

    private static final String VALID_BODY = """
            {"name":"Jose Lema","gender":"MALE","age":35,"identification":"1712345678",
             "address":"Otavalo sn y principal","phone":"098254785","password":"Clave1234","active":true}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreateClientUseCase createClient;
    @MockitoBean
    private GetClientUseCase getClient;
    @MockitoBean
    private ListClientsUseCase listClients;
    @MockitoBean
    private UpdateClientUseCase updateClient;
    @MockitoBean
    private DeleteClientUseCase deleteClient;

    private final Client client = storedClient(0);

    @Test
    void f1Ac1CreatesAClientAndNeverReturnsThePassword() throws Exception {
        when(createClient.execute(new CreateClientCommand(details(), "Clave1234", true))).thenReturn(client);

        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/clients/" + client.getClientId()))
                .andExpect(jsonPath("$.clientId").value(client.getClientId().toString()))
                .andExpect(jsonPath("$.name").value("Jose Lema"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void rejectsInvalidFieldsWithValidationError() throws Exception {
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"J","gender":"MALE","age":35,"identification":"1712345678",
                         "address":"Otavalo sn y principal","phone":"abc","password":"short"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.title").value("Invalid input"))
                .andExpect(jsonPath("$.type").value("https://bank.local/errors/validation-error"))
                .andExpect(jsonPath("$.instance").value("/clients"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Name must be between 2 and 100 characters long"))
                .andExpect(jsonPath("$.errors[1].field").value("password"))
                .andExpect(jsonPath("$.errors[2].field").value("phone"));
        verifyNoInteractions(createClient);
    }

    @Test
    void rejectsAnUnknownEnumValueAsMalformedJson() throws Exception {
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"MALE\"", "\"ROBOT\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void rejectsABodyThatIsNotJson() throws Exception {
        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void f1Ac2AnswersConflictForADuplicateIdentification() throws Exception {
        when(createClient.execute(any())).thenThrow(DuplicateResourceException.identification("1712345678"));

        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.title").value("Duplicate resource"))
                .andExpect(jsonPath("$.detail").value("A client with identification 1712345678 already exists"));
    }

    @Test
    void f1Ac3AnswersUnprocessableForAnUnderageClient() throws Exception {
        when(createClient.execute(any())).thenThrow(new BusinessRuleException("The client must be at least 18 years old"));

        mvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"age\":35", "\"age\":17")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.detail").value("The client must be at least 18 years old"));
    }

    @Test
    void listsClientsOnePageAtATime() throws Exception {
        when(listClients.execute(new PageQuery(1, 5)))
                .thenReturn(new PagedResult<>(List.of(client), 1, 5, 6));

        mvc.perform(get("/clients").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].clientId").value(client.getClientId().toString()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void usesTheDefaultPage() throws Exception {
        when(listClients.execute(new PageQuery(0, 20))).thenReturn(new PagedResult<>(List.of(), 0, 20, 0));

        mvc.perform(get("/clients")).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void rejectsAPageSizeAboveTheLimit() throws Exception {
        mvc.perform(get("/clients").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").value("size must be between 1 and 100"));
    }

    @Test
    void getsOneClient() throws Exception {
        when(getClient.execute(client.getClientId())).thenReturn(client);

        mvc.perform(get("/clients/{clientId}", client.getClientId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identification").value("1712345678"));
    }

    @Test
    void answersNotFoundForAnUnknownClient() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(getClient.execute(unknown)).thenThrow(ResourceNotFoundException.client(unknown));

        mvc.perform(get("/clients/{clientId}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/clients/" + unknown));
    }

    @Test
    void rejectsAClientIdThatIsNotAUuid() throws Exception {
        mvc.perform(get("/clients/{clientId}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.detail").value("Parameter 'clientId' has an invalid value"));
    }

    @Test
    void replacesAClient() throws Exception {
        ClientChanges expected = new ClientChanges("Jose Lema", details().gender(), 35, "1712345678",
                "Otavalo sn y principal", "098254785", null, false);
        when(updateClient.execute(client.getClientId(), expected)).thenReturn(client);

        mvc.perform(put("/clients/{clientId}", client.getClientId()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"password\":\"Clave1234\",", "").replace("true", "false")))
                .andExpect(status().isOk());
        verify(updateClient).execute(client.getClientId(), expected);
    }

    @Test
    void putRequiresEveryField() throws Exception {
        mvc.perform(put("/clients/{clientId}", client.getClientId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jose Lema\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(6)));
    }

    @Test
    void f1Ac4PatchSendsOnlyThePresentFields() throws Exception {
        ClientChanges onlyActive = new ClientChanges(null, null, null, null, null, null, null, false);
        when(updateClient.execute(client.getClientId(), onlyActive)).thenReturn(client);

        mvc.perform(patch("/clients/{clientId}", client.getClientId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());
        verify(updateClient).execute(eq(client.getClientId()), eq(onlyActive));
    }

    @Test
    void patchValidatesThePresentFields() throws Exception {
        mvc.perform(patch("/clients/{clientId}", client.getClientId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":200}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Age must be between 0 and 120"));
    }

    @Test
    void f1Ac5DeletesAClient() throws Exception {
        mvc.perform(delete("/clients/{clientId}", client.getClientId())).andExpect(status().isNoContent());
        verify(deleteClient).execute(client.getClientId());
    }

    @Test
    void answersMethodNotAllowed() throws Exception {
        mvc.perform(post("/clients/{clientId}", client.getClientId()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void answersUnsupportedMediaType() throws Exception {
        mvc.perform(post("/clients").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void answersNotFoundForAnUnknownRoute() throws Exception {
        mvc.perform(get("/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void usesTheStatusNameAsCodeForOtherHttpErrors() throws Exception {
        when(getClient.execute(client.getClientId())).thenReturn(client);

        mvc.perform(get("/clients/{clientId}", client.getClientId()).accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void hidesTheDetailsOfUnexpectedErrors() throws Exception {
        when(getClient.execute(client.getClientId())).thenThrow(new IllegalStateException("connection refused"));

        mvc.perform(get("/clients/{clientId}", client.getClientId()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."));
    }

    @Test
    void echoesAValidCorrelationId() throws Exception {
        when(getClient.execute(client.getClientId())).thenReturn(client);

        mvc.perform(get("/clients/{clientId}", client.getClientId()).header(CorrelationIdFilter.HEADER, "abc-123"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "abc-123"));
    }

    @Test
    void replacesAnInvalidCorrelationId() throws Exception {
        when(getClient.execute(client.getClientId())).thenReturn(client);

        mvc.perform(get("/clients/{clientId}", client.getClientId()).header(CorrelationIdFilter.HEADER, "bad id!"))
                .andExpect(header().string(CorrelationIdFilter.HEADER,
                        matchesPattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")));
    }
}
