package com.bank.clients.domain.port;

import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.pagination.PageQuery;
import com.bank.clients.domain.pagination.PagedResult;

import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link Client} (repository pattern, ADR-005). */
public interface ClientRepository {

    Client save(Client client);

    Optional<Client> findByClientId(UUID clientId);

    PagedResult<Client> findAll(PageQuery query);

    boolean existsByIdentification(String identification);

    /** Detects a duplicate identification when updating {@code clientId} itself. */
    boolean existsByIdentificationAndClientIdNot(String identification, UUID clientId);

    void delete(Client client);
}
