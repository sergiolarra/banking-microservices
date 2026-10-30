package com.bank.accounts.infrastructure.persistence;

import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.model.ClientSnapshot;
import com.bank.accounts.domain.model.Movement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/** Converts between the domain model and the JPA entities (ADR-005). */
@Mapper
interface PersistenceMapper {

    /** Copies the domain state onto a new or managed entity; id and version belong to JPA. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    void copyToEntity(Account account, @MappingTarget AccountEntity entity);

    @Mapping(target = "newEntity", ignore = true)
    void copyToEntity(ClientSnapshot snapshot, @MappingTarget ClientSnapshotEntity entity);

    /** Only what a correction may change; account and date are fixed once recorded. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "movementDate", ignore = true)
    void copyToEntity(Movement movement, @MappingTarget MovementEntity entity);

    default Account toDomain(AccountEntity entity) {
        return Account.restore(entity.getId(), entity.getAccountNumber(), entity.getAccountType(),
                entity.getInitialBalance(), entity.getAvailableBalance(), entity.isActive(), entity.getClientId());
    }

    default Movement toDomain(MovementEntity entity) {
        return Movement.restore(entity.getId(), entity.getAccount().getAccountNumber(), entity.getMovementDate(),
                entity.getAmount(), entity.getBalance());
    }

    default ClientSnapshot toDomain(ClientSnapshotEntity entity) {
        return ClientSnapshot.restore(entity.getClientId(), entity.getName(), entity.getIdentification(),
                entity.isActive(), entity.isDeleted(), entity.getVersion(), entity.getUpdatedAt());
    }
}
