package com.bank.accounts.domain.port;

import com.bank.accounts.domain.model.Movement;
import com.bank.accounts.domain.pagination.PageQuery;
import com.bank.accounts.domain.pagination.PagedResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link Movement}. */
public interface MovementRepository {

    Movement save(Movement movement);

    Optional<Movement> findById(long id);

    /** The most recent movement of the account: latest date, then highest id. */
    Optional<Movement> findLatestByAccountNumber(String accountNumber);

    /**
     * Movements of every account of the client with {@code from <= date < to}, oldest
     * first, in a single query (F4-AC2, F4-AC6).
     */
    List<Movement> findByClientIdBetween(UUID clientId, Instant from, Instant to);

    /**
     * Newest first.
     *
     * @param accountNumber optional filter; {@code null} lists every movement
     */
    PagedResult<Movement> findAll(String accountNumber, PageQuery query);
}
