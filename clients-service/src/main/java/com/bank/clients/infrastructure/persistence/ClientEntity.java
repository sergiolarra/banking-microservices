package com.bank.clients.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** JPA mapping of a client; its PK is also the FK to {@code person} (ADR-006). */
@Entity
@Table(name = "client")
@PrimaryKeyJoinColumn(name = "person_id")
@Getter
@Setter
@NoArgsConstructor
public class ClientEntity extends PersonEntity {

    @Column(name = "client_id", nullable = false, unique = true, updatable = false)
    private UUID clientId;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false)
    private boolean active;
}
