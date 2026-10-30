package com.bank.accounts.domain.port;

import com.bank.accounts.domain.model.ClientSnapshot;

import java.util.Optional;
import java.util.UUID;

/** Persistence port for the local client replica (ADR-008). */
public interface ClientSnapshotRepository {

    Optional<ClientSnapshot> findById(UUID clientId);

    /** Inserts the snapshot or replaces the stored one. */
    ClientSnapshot save(ClientSnapshot snapshot);
}
