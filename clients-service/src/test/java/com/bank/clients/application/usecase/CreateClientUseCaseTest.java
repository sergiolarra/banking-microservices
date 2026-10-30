package com.bank.clients.application.usecase;

import com.bank.clients.application.command.CreateClientCommand;
import com.bank.clients.domain.event.ClientEvent;
import com.bank.clients.domain.event.ClientEventType;
import com.bank.clients.domain.exception.DuplicateResourceException;
import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.port.ClientEventPublisher;
import com.bank.clients.domain.port.ClientRepository;
import com.bank.clients.domain.port.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.bank.clients.ClientFixtures.FIXED_CLOCK;
import static com.bank.clients.ClientFixtures.NOW;
import static com.bank.clients.ClientFixtures.details;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateClientUseCaseTest {

    @Mock
    private ClientRepository clients;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private ClientEventPublisher events;

    private CreateClientUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateClientUseCase(clients, passwordHasher, events, FIXED_CLOCK);
        when(passwordHasher.hash("Clave1234")).thenReturn("$2a$10$hash");
    }

    @Test
    void storesTheClientWithTheHashedPasswordAndPublishesClientCreated() {
        when(clients.save(any())).thenAnswer(invocation -> storedCopy(invocation.getArgument(0)));

        Client saved = useCase.execute(new CreateClientCommand(details(), "Clave1234", null));

        ArgumentCaptor<Client> stored = ArgumentCaptor.forClass(Client.class);
        verify(clients).save(stored.capture());
        assertThat(stored.getValue().getPasswordHash()).isEqualTo("$2a$10$hash");
        assertThat(stored.getValue().isActive()).isTrue();
        verify(events).publish(new ClientEvent(ClientEventType.CREATED, saved.getClientId(), "Jose Lema",
                "1712345678", true, 0, NOW));
    }

    @Test
    void f1Ac2RejectsADuplicateIdentification() {
        when(clients.existsByIdentification("1712345678")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(new CreateClientCommand(details(), "Clave1234", true)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Ya existe un cliente con la identificación 1712345678");
        verify(clients, never()).save(any());
        verifyNoInteractions(events);
    }

    private static Client storedCopy(Client client) {
        return Client.restore(10L, client.getClientId(), 0L, client.getDetails(), client.getPasswordHash(),
                client.isActive());
    }
}
