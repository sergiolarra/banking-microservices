package com.bank.clients.application.usecase;

import com.bank.clients.domain.event.ClientEvent;
import com.bank.clients.domain.event.ClientEventType;
import com.bank.clients.domain.exception.ResourceNotFoundException;
import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.port.ClientEventPublisher;
import com.bank.clients.domain.port.ClientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static com.bank.clients.ClientFixtures.FIXED_CLOCK;
import static com.bank.clients.ClientFixtures.NOW;
import static com.bank.clients.ClientFixtures.storedClient;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteClientUseCaseTest {

    @Mock
    private ClientRepository clients;
    @Mock
    private ClientEventPublisher events;

    @Test
    void f1Ac5DeletesTheClientAndPublishesClientDeletedWithTheNextVersion() {
        Client client = storedClient(3);
        when(clients.findByClientId(client.getClientId())).thenReturn(Optional.of(client));

        new DeleteClientUseCase(clients, events, FIXED_CLOCK).execute(client.getClientId());

        InOrder order = inOrder(clients, events);
        order.verify(clients).delete(client);
        order.verify(events).publish(new ClientEvent(ClientEventType.DELETED, client.getClientId(), "Jose Lema",
                "1712345678", true, 4, NOW));
    }

    @Test
    void failsWhenTheClientDoesNotExist() {
        UUID unknown = UUID.randomUUID();
        when(clients.findByClientId(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DeleteClientUseCase(clients, events, FIXED_CLOCK).execute(unknown))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(clients, never()).delete(any());
        verifyNoInteractions(events);
    }
}
