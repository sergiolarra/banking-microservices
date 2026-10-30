package com.bank.clients.infrastructure.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/** A client event waiting to be (or already) relayed to RabbitMQ (ADR-009). */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(nullable = false, length = 50)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** The id is assigned by us, so tell Spring Data to insert instead of merging. */
    @Transient
    private boolean newEntity;

    public OutboxEventEntity(UUID id, UUID aggregateId, String type, String payload, Instant createdAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
        this.createdAt = createdAt;
        this.newEntity = true;
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.newEntity = false;
    }
}
