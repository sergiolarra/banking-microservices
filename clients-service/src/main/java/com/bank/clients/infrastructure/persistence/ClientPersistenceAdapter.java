package com.bank.clients.infrastructure.persistence;

import com.bank.clients.domain.exception.DuplicateResourceException;
import com.bank.clients.domain.exception.ResourceNotFoundException;
import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.pagination.PageQuery;
import com.bank.clients.domain.pagination.PagedResult;
import com.bank.clients.domain.port.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Implements the {@link ClientRepository} port with Spring Data JPA. */
@Component
@RequiredArgsConstructor
class ClientPersistenceAdapter implements ClientRepository {

    private static final String IDENTIFICATION_CONSTRAINT = "uk_person_identification";

    private final ClientJpaRepository repository;
    private final ClientPersistenceMapper mapper;

    /**
     * Flushes immediately so the returned client carries the new version (needed by
     * the events) and so a unique-constraint race surfaces here, where it can be
     * translated into a domain error.
     */
    @Override
    public Client save(Client client) {
        ClientEntity entity = client.getId() == null
                ? new ClientEntity()
                : repository.findById(client.getId())
                        .orElseThrow(() -> ResourceNotFoundException.client(client.getClientId()));
        mapper.copyToEntity(client, entity);
        try {
            return mapper.toDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            if (isIdentificationConflict(e)) {
                throw DuplicateResourceException.identification(client.getIdentification());
            }
            throw e;
        }
    }

    @Override
    public Optional<Client> findByClientId(UUID clientId) {
        return repository.findByClientId(clientId).map(mapper::toDomain);
    }

    @Override
    public PagedResult<Client> findAll(PageQuery query) {
        Page<ClientEntity> page = repository.findAll(PageRequest.of(query.page(), query.size(), Sort.by("id")));
        return new PagedResult<>(page.map(mapper::toDomain).getContent(), query.page(), query.size(),
                page.getTotalElements());
    }

    @Override
    public boolean existsByIdentification(String identification) {
        return repository.existsByIdentification(identification);
    }

    @Override
    public boolean existsByIdentificationAndClientIdNot(String identification, UUID clientId) {
        return repository.existsByIdentificationAndClientIdNot(identification, clientId);
    }

    @Override
    public void delete(Client client) {
        repository.deleteById(client.getId());
    }

    private static boolean isIdentificationConflict(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(IDENTIFICATION_CONSTRAINT);
    }
}
