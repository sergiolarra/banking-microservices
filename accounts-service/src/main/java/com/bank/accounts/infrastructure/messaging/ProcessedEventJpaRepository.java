package com.bank.accounts.infrastructure.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, UUID> {
}
