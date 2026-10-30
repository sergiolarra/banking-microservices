package com.bank.clients.domain.event;

import com.bank.clients.domain.model.Client;
import org.junit.jupiter.api.Test;

import static com.bank.clients.ClientFixtures.NOW;
import static com.bank.clients.ClientFixtures.PASSWORD_HASH;
import static com.bank.clients.ClientFixtures.details;
import static com.bank.clients.ClientFixtures.storedClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientEventTest {

    @Test
    void createdAndUpdatedCarryTheStoredVersion() {
        Client client = storedClient(4);

        assertThat(ClientEvent.created(client, NOW).version()).isEqualTo(4);
        assertThat(ClientEvent.updated(client, NOW))
                .isEqualTo(new ClientEvent(ClientEventType.UPDATED, client.getClientId(), "Jose Lema", "1712345678",
                        true, 4, NOW));
    }

    @Test
    void deletedUsesTheNextVersion() {
        ClientEvent event = ClientEvent.deleted(storedClient(4), NOW);

        assertThat(event.type()).isEqualTo(ClientEventType.DELETED);
        assertThat(event.version()).isEqualTo(5);
    }

    @Test
    void requiresAStoredClient() {
        Client unsaved = Client.create(details(), PASSWORD_HASH, true);

        assertThatThrownBy(() -> ClientEvent.created(unsaved, NOW)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void usesTheContractEventNames() {
        assertThat(ClientEventType.CREATED.eventName()).isEqualTo("client.created");
        assertThat(ClientEventType.UPDATED.eventName()).isEqualTo("client.updated");
        assertThat(ClientEventType.DELETED.eventName()).isEqualTo("client.deleted");
    }
}
