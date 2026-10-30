package com.bank.accounts.application.usecase;

import com.bank.accounts.application.command.CreateAccountCommand;
import com.bank.accounts.domain.exception.ClientUnavailableException;
import com.bank.accounts.domain.exception.DuplicateResourceException;
import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.port.AccountRepository;
import com.bank.accounts.domain.port.ClientSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F1: opens an account for a client known to this service and able to operate (F1-AC6). */
@Service
public class CreateAccountUseCase {

    private final AccountRepository accounts;
    private final ClientSnapshotRepository clients;

    public CreateAccountUseCase(AccountRepository accounts, ClientSnapshotRepository clients) {
        this.accounts = accounts;
        this.clients = clients;
    }

    @Transactional
    public Account execute(CreateAccountCommand command) {
        clients.findById(command.clientId())
                .orElseThrow(() -> ClientUnavailableException.unknown(command.clientId()))
                .ensureCanOperate();
        Account account = Account.open(command.accountNumber(), command.accountType(), command.initialBalance(),
                command.clientId(), command.active());
        if (accounts.existsByAccountNumber(account.getAccountNumber())) {
            throw DuplicateResourceException.accountNumber(account.getAccountNumber());
        }
        return accounts.save(account);
    }
}
