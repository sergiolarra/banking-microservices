package com.bank.accounts.infrastructure.persistence;

import com.bank.accounts.domain.exception.DuplicateResourceException;
import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.pagination.PageQuery;
import com.bank.accounts.domain.pagination.PagedResult;
import com.bank.accounts.domain.port.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Implements the {@link AccountRepository} port with Spring Data JPA. */
@Component
@RequiredArgsConstructor
class AccountPersistenceAdapter implements AccountRepository {

    private static final String ACCOUNT_NUMBER_CONSTRAINT = "uk_account_account_number";

    private final AccountJpaRepository repository;
    private final PersistenceMapper mapper;

    /** Flushes so a unique-constraint race surfaces here and becomes a domain error. */
    @Override
    public Account save(Account account) {
        AccountEntity entity = account.getId() == null
                ? new AccountEntity()
                : repository.findById(account.getId())
                        .orElseThrow(() -> ResourceNotFoundException.account(account.getAccountNumber()));
        mapper.copyToEntity(account, entity);
        try {
            return mapper.toDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            if (isAccountNumberConflict(e)) {
                throw DuplicateResourceException.accountNumber(account.getAccountNumber());
            }
            throw e;
        }
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return repository.findByAccountNumber(accountNumber).map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findByAccountNumberForUpdate(String accountNumber) {
        return repository.findByAccountNumberForUpdate(accountNumber).map(mapper::toDomain);
    }

    @Override
    public PagedResult<Account> findAll(UUID clientId, PageQuery query) {
        PageRequest pageable = PageRequest.of(query.page(), query.size(), Sort.by("id"));
        Page<AccountEntity> page = clientId == null
                ? repository.findAll(pageable)
                : repository.findByClientId(clientId, pageable);
        return new PagedResult<>(page.map(mapper::toDomain).getContent(), query.page(), query.size(),
                page.getTotalElements());
    }

    @Override
    public List<Account> findAllByClientId(UUID clientId) {
        return repository.findAllByClientIdOrderByAccountNumber(clientId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsByAccountNumber(String accountNumber) {
        return repository.existsByAccountNumber(accountNumber);
    }

    private static boolean isAccountNumberConflict(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(ACCOUNT_NUMBER_CONSTRAINT);
    }
}
