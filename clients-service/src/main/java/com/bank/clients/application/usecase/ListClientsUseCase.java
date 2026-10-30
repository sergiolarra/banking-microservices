package com.bank.clients.application.usecase;

import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.pagination.PageQuery;
import com.bank.clients.domain.pagination.PagedResult;
import com.bank.clients.domain.port.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F1: lists clients one page at a time. */
@Service
public class ListClientsUseCase {

    private final ClientRepository clients;

    public ListClientsUseCase(ClientRepository clients) {
        this.clients = clients;
    }

    @Transactional(readOnly = true)
    public PagedResult<Client> execute(PageQuery query) {
        return clients.findAll(query);
    }
}
