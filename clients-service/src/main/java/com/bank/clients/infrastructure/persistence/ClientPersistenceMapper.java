package com.bank.clients.infrastructure.persistence;

import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.model.PersonalDetails;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/** Converts between the domain model and the JPA entities (ADR-005). */
@Mapper
interface ClientPersistenceMapper {

    /** Copies the domain state onto a new or managed entity; id and version belong to JPA. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "password", source = "passwordHash")
    void copyToEntity(Client client, @MappingTarget ClientEntity entity);

    PersonalDetails toDetails(PersonEntity entity);

    default Client toDomain(ClientEntity entity) {
        return Client.restore(entity.getId(), entity.getClientId(), entity.getVersion(), toDetails(entity),
                entity.getPassword(), entity.isActive());
    }
}
