package com.bank.accounts.application.usecase;

import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.pagination.PageQuery;
import com.bank.accounts.domain.pagination.PagedResult;
import com.bank.accounts.domain.port.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** F1: lists accounts, optionally only those of one client. */
@Service
public class ListAccountsUseCase {

    private final AccountRepository accounts;

    public ListAccountsUseCase(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /** @param clientId optional filter */
    @Transactional(readOnly = true)
    public PagedResult<Account> execute(UUID clientId, PageQuery query) {
        return accounts.findAll(clientId, query);
    }
}
