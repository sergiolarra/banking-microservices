package com.bank.accounts.infrastructure.persistence;

import com.bank.accounts.domain.model.ClientSnapshot;
import com.bank.accounts.domain.port.ClientSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Implements the {@link ClientSnapshotRepository} port with Spring Data JPA. */
@Component
@RequiredArgsConstructor
class ClientSnapshotPersistenceAdapter implements ClientSnapshotRepository {

    private final ClientSnapshotJpaRepository repository;
    private final PersistenceMapper mapper;

    @Override
    public Optional<ClientSnapshot> findById(UUID clientId) {
        return repository.findById(clientId).map(mapper::toDomain);
    }

    @Override
    public ClientSnapshot save(ClientSnapshot snapshot) {
        ClientSnapshotEntity entity = repository.findById(snapshot.getClientId())
                .orElseGet(() -> new ClientSnapshotEntity(snapshot.getClientId()));
        mapper.copyToEntity(snapshot, entity);
        return mapper.toDomain(repository.save(entity));
    }
}
