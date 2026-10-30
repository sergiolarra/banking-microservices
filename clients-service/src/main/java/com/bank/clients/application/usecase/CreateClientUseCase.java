package com.bank.clients.application.usecase;

import com.bank.clients.application.command.CreateClientCommand;
import com.bank.clients.domain.event.ClientEvent;
import com.bank.clients.domain.exception.DuplicateResourceException;
import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.port.ClientEventPublisher;
import com.bank.clients.domain.port.ClientRepository;
import com.bank.clients.domain.port.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** F1: registers a client and announces it with {@code client.created}. */
@Service
public class CreateClientUseCase {

    private final ClientRepository clients;
    private final PasswordHasher passwordHasher;
    private final ClientEventPublisher events;
    private final Clock clock;

    public CreateClientUseCase(
            ClientRepository clients, PasswordHasher passwordHasher, ClientEventPublisher events, Clock clock) {
        this.clients = clients;
        this.passwordHasher = passwordHasher;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Client execute(CreateClientCommand command) {
        Client client = Client.create(command.details(), passwordHasher.hash(command.rawPassword()), command.active());
        if (clients.existsByIdentification(client.getIdentification())) {
            throw DuplicateResourceException.identification(client.getIdentification());
        }
        Client saved = clients.save(client);
        events.publish(ClientEvent.created(saved, clock.instant()));
        return saved;
    }
}
