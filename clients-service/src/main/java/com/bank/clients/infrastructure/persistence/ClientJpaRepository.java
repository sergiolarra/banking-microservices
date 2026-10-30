package com.bank.clients.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ClientJpaRepository extends JpaRepository<ClientEntity, Long> {

    Optional<ClientEntity> findByClientId(UUID clientId);

    boolean existsByIdentification(String identification);

    boolean existsByIdentificationAndClientIdNot(String identification, UUID clientId);
}
