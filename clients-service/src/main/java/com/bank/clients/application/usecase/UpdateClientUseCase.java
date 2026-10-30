package com.bank.clients.application.usecase;

import com.bank.clients.application.command.ClientChanges;
import com.bank.clients.domain.event.ClientEvent;
import com.bank.clients.domain.exception.DuplicateResourceException;
import com.bank.clients.domain.exception.ResourceNotFoundException;
import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.port.ClientEventPublisher;
import com.bank.clients.domain.port.ClientRepository;
import com.bank.clients.domain.port.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** F1: full (PUT) or partial (PATCH) update of a client; emits {@code client.updated}. */
@Service
public class UpdateClientUseCase {

    private final ClientRepository clients;
    private final PasswordHasher passwordHasher;
    private final ClientEventPublisher events;
    private final Clock clock;

    public UpdateClientUseCase(
            ClientRepository clients, PasswordHasher passwordHasher, ClientEventPublisher events, Clock clock) {
        this.clients = clients;
        this.passwordHasher = passwordHasher;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Client execute(UUID clientId, ClientChanges changes) {
        Client client = clients.findByClientId(clientId).orElseThrow(() -> ResourceNotFoundException.client(clientId));

        client.updateDetails(changes.applyTo(client.getDetails()));
        if (clients.existsByIdentificationAndClientIdNot(client.getIdentification(), clientId)) {
            throw DuplicateResourceException.identification(client.getIdentification());
        }
        if (changes.rawPassword() != null) {
            client.changePassword(passwordHasher.hash(changes.rawPassword()));
        }
        applyActiveState(client, changes.active());

        Client saved = clients.save(client);
        events.publish(ClientEvent.updated(saved, clock.instant()));
        return saved;
    }

    private static void applyActiveState(Client client, Boolean active) {
        if (Boolean.TRUE.equals(active)) {
            client.activate();
        } else if (Boolean.FALSE.equals(active)) {
            client.deactivate();
        }
    }
}
