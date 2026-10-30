package com.bank.accounts.domain.port;

import com.bank.accounts.domain.model.Movement;
import com.bank.accounts.domain.pagination.PageQuery;
import com.bank.accounts.domain.pagination.PagedResult;

import java.util.Optional;

/** Persistence port for {@link Movement}. */
public interface MovementRepository {

    Movement save(Movement movement);

    Optional<Movement> findById(long id);

    /** The most recent movement of the account: latest date, then highest id. */
    Optional<Movement> findLatestByAccountNumber(String accountNumber);

    /**
     * Newest first.
     *
     * @param accountNumber optional filter; {@code null} lists every movement
     */
    PagedResult<Movement> findAll(String accountNumber, PageQuery query);
}
