package com.bank.clients.application.usecase;

import com.bank.clients.domain.event.ClientEvent;
import com.bank.clients.domain.exception.ResourceNotFoundException;
import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.port.ClientEventPublisher;
import com.bank.clients.domain.port.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * F1: deletes a client and emits {@code client.deleted}, so accounts-service can
 * deactivate its accounts while keeping their history (ADR-016).
 */
@Service
public class DeleteClientUseCase {

    private final ClientRepository clients;
    private final ClientEventPublisher events;
    private final Clock clock;

    public DeleteClientUseCase(ClientRepository clients, ClientEventPublisher events, Clock clock) {
        this.clients = clients;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void execute(UUID clientId) {
        Client client = clients.findByClientId(clientId).orElseThrow(() -> ResourceNotFoundException.client(clientId));
        clients.delete(client);
        events.publish(ClientEvent.deleted(client, clock.instant()));
    }
}
