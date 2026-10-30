package com.bank.accounts.infrastructure.messaging;

import com.bank.accounts.application.command.ClientEventCommand;
import com.bank.accounts.application.command.ClientEventType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.bank.accounts.AccountFixtures.CLIENT_ID;
import static com.bank.accounts.AccountFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientEventMessageTest {

    private static ClientEventMessage message(String type) {
        return new ClientEventMessage(UUID.randomUUID(), type, NOW, 4, "corr-1",
                new ClientEventMessage.Data(CLIENT_ID, "Jose Lema", "1712345678", true));
    }

    @Test
    void mapsEveryContractEventName() {
        assertThat(message("client.created").toCommand())
                .isEqualTo(new ClientEventCommand(ClientEventType.CREATED, CLIENT_ID, "Jose Lema", "1712345678",
                        true, 4, NOW));
        assertThat(message("client.updated").toCommand().type()).isEqualTo(ClientEventType.UPDATED);
        assertThat(message("client.deleted").toCommand().type()).isEqualTo(ClientEventType.DELETED);
        assertThat(ClientEventType.DELETED.eventName()).isEqualTo("client.deleted");
    }

    @Test
    void rejectsAnUnknownEventType() {
        assertThatThrownBy(() -> message("client.renamed").toCommand())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown client event type: client.renamed");
    }
}
