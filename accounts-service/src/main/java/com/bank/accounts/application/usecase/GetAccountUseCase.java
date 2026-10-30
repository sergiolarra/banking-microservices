package com.bank.accounts.application.usecase;

import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.port.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F1: reads one account by its number. */
@Service
public class GetAccountUseCase {

    private final AccountRepository accounts;

    public GetAccountUseCase(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public Account execute(String accountNumber) {
        return accounts.findByAccountNumber(accountNumber)
                .orElseThrow(() -> ResourceNotFoundException.account(accountNumber));
    }
}
