package com.bank.accounts.domain.port;

import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.pagination.PageQuery;
import com.bank.accounts.domain.pagination.PagedResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link Account} (repository pattern, ADR-005). */
public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Same as {@link #findByAccountNumber} but locks the account until the transaction
     * ends, so balance changes on one account are serialized (ADR-013).
     */
    Optional<Account> findByAccountNumberForUpdate(String accountNumber);

    /** @param clientId optional filter; {@code null} lists every account */
    PagedResult<Account> findAll(UUID clientId, PageQuery query);

    List<Account> findAllByClientId(UUID clientId);

    boolean existsByAccountNumber(String accountNumber);
}
