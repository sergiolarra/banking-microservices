package com.bank.clients.application.usecase;

import com.bank.clients.domain.exception.ResourceNotFoundException;
import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.port.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** F1: reads one client by its business key. */
@Service
public class GetClientUseCase {

    private final ClientRepository clients;

    public GetClientUseCase(ClientRepository clients) {
        this.clients = clients;
    }

    @Transactional(readOnly = true)
    public Client execute(UUID clientId) {
        return clients.findByClientId(clientId).orElseThrow(() -> ResourceNotFoundException.client(clientId));
    }
}
