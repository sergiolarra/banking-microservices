package com.bank.clients.application.usecase;

import com.bank.clients.domain.exception.ResourceNotFoundException;
import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.pagination.PageQuery;
import com.bank.clients.domain.pagination.PagedResult;
import com.bank.clients.domain.port.ClientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.bank.clients.ClientFixtures.storedClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** GetClientUseCase and ListClientsUseCase. */
@ExtendWith(MockitoExtension.class)
class ReadClientUseCasesTest {

    @Mock
    private ClientRepository clients;

    @Test
    void getsAClientByItsClientId() {
        Client client = storedClient(0);
        when(clients.findByClientId(client.getClientId())).thenReturn(Optional.of(client));

        assertThat(new GetClientUseCase(clients).execute(client.getClientId())).isSameAs(client);
    }

    @Test
    void failsWhenTheClientDoesNotExist() {
        UUID unknown = UUID.randomUUID();
        when(clients.findByClientId(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetClientUseCase(clients).execute(unknown))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listsOnePage() {
        PageQuery query = new PageQuery(0, 20);
        PagedResult<Client> page = new PagedResult<>(List.of(storedClient(0)), 0, 20, 1);
        when(clients.findAll(query)).thenReturn(page);

        assertThat(new ListClientsUseCase(clients).execute(query)).isSameAs(page);
    }
}
