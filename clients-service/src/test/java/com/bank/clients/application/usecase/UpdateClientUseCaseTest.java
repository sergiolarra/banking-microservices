package com.bank.clients.application.usecase;

import com.bank.clients.application.command.ClientChanges;
import com.bank.clients.domain.event.ClientEvent;
import com.bank.clients.domain.event.ClientEventType;
import com.bank.clients.domain.exception.DuplicateResourceException;
import com.bank.clients.domain.exception.ResourceNotFoundException;
import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.model.Gender;
import com.bank.clients.domain.port.ClientEventPublisher;
import com.bank.clients.domain.port.ClientRepository;
import com.bank.clients.domain.port.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static com.bank.clients.ClientFixtures.FIXED_CLOCK;
import static com.bank.clients.ClientFixtures.NOW;
import static com.bank.clients.ClientFixtures.PASSWORD_HASH;
import static com.bank.clients.ClientFixtures.storedClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateClientUseCaseTest {

    private static final ClientChanges NO_CHANGES = new ClientChanges(null, null, null, null, null, null, null, null);

    @Mock
    private ClientRepository clients;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private ClientEventPublisher events;

    private UpdateClientUseCase useCase;
    private Client current;

    @BeforeEach
    void setUp() {
        useCase = new UpdateClientUseCase(clients, passwordHasher, events, FIXED_CLOCK);
        current = storedClient(2);
    }

    private Client stored() {
        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clients).save(captor.capture());
        return captor.getValue();
    }

    private void givenTheClientExists() {
        when(clients.findByClientId(current.getClientId())).thenReturn(Optional.of(current));
        when(clients.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void appliesOnlyThePresentFieldsAndPublishesClientUpdated() {
        givenTheClientExists();

        useCase.execute(current.getClientId(),
                new ClientChanges("Jose A. Lema", null, 36, null, null, null, null, null));

        Client stored = stored();
        assertThat(stored.getName()).isEqualTo("Jose A. Lema");
        assertThat(stored.getAge()).isEqualTo(36);
        assertThat(stored.getGender()).isEqualTo(Gender.MALE);
        assertThat(stored.getIdentification()).isEqualTo("1712345678");
        assertThat(stored.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(stored.isActive()).isTrue();
        verify(events).publish(new ClientEvent(ClientEventType.UPDATED, current.getClientId(), "Jose A. Lema",
                "1712345678", true, 2, NOW));
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void f1Ac4DeactivatesWithoutTouchingTheRest() {
        givenTheClientExists();

        useCase.execute(current.getClientId(), new ClientChanges(null, null, null, null, null, null, null, false));

        assertThat(stored().isActive()).isFalse();
        assertThat(stored().getDetails()).isEqualTo(current.getDetails());
    }

    @Test
    void activatesAnInactiveClient() {
        current.deactivate();
        givenTheClientExists();

        useCase.execute(current.getClientId(), new ClientChanges(null, null, null, null, null, null, null, true));

        assertThat(stored().isActive()).isTrue();
    }

    @Test
    void hashesANewPassword() {
        givenTheClientExists();
        when(passwordHasher.hash("NuevaClave9")).thenReturn("$2a$10$newHash");

        useCase.execute(current.getClientId(),
                new ClientChanges(null, null, null, null, null, null, "NuevaClave9", null));

        assertThat(stored().getPasswordHash()).isEqualTo("$2a$10$newHash");
    }

    @Test
    void rejectsAnIdentificationUsedByAnotherClient() {
        when(clients.findByClientId(current.getClientId())).thenReturn(Optional.of(current));
        when(clients.existsByIdentificationAndClientIdNot("0912345678", current.getClientId())).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(current.getClientId(),
                new ClientChanges(null, null, null, "0912345678", null, null, null, null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(clients, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void failsWhenTheClientDoesNotExist() {
        UUID unknown = UUID.randomUUID();
        when(clients.findByClientId(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(unknown, NO_CHANGES))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No client exists with clientId " + unknown);
    }
}
