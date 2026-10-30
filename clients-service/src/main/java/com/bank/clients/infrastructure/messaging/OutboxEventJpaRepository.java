package com.bank.clients.infrastructure.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Locks the oldest pending events. {@code SKIP LOCKED} lets several replicas relay
     * in parallel without sending the same event twice (ADR-009).
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> lockPending(@Param("limit") int limit);
}
