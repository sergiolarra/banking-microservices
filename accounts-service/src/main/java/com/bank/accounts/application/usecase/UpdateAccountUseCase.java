package com.bank.accounts.application.usecase;

import com.bank.accounts.application.command.AccountChanges;
import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.port.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F1: changes the type or state of an account (PUT and PATCH). The account is locked
 * like for a movement, so a concurrent balance change is never overwritten (ADR-013).
 */
@Service
public class UpdateAccountUseCase {

    private final AccountRepository accounts;

    public UpdateAccountUseCase(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public Account execute(String accountNumber, AccountChanges changes) {
        Account account = accounts.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> ResourceNotFoundException.account(accountNumber));
        if (changes.accountType() != null) {
            account.changeType(changes.accountType());
        }
        if (Boolean.TRUE.equals(changes.active())) {
            account.activate();
        } else if (Boolean.FALSE.equals(changes.active())) {
            account.deactivate();
        }
        return accounts.save(account);
    }
}
