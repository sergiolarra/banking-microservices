package com.bank.accounts.application.usecase;

import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.model.Movement;
import com.bank.accounts.domain.port.AccountRepository;
import com.bank.accounts.domain.port.ClientSnapshotRepository;
import com.bank.accounts.domain.port.MovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * F2-AC8: changes the amount of the latest movement of an account. The account is
 * locked before the latest movement is read, so no other movement can slip in between.
 */
@Service
public class CorrectMovementUseCase {

    private final AccountRepository accounts;
    private final MovementRepository movements;
    private final ClientSnapshotRepository clients;

    public CorrectMovementUseCase(AccountRepository accounts, MovementRepository movements,
                                  ClientSnapshotRepository clients) {
        this.accounts = accounts;
        this.movements = movements;
        this.clients = clients;
    }

    @Transactional
    public Movement execute(long movementId, BigDecimal newAmount) {
        Movement movement = movements.findById(movementId)
                .orElseThrow(() -> ResourceNotFoundException.movement(movementId));
        Account account = accounts.findByAccountNumberForUpdate(movement.getAccountNumber())
                .orElseThrow(() -> ResourceNotFoundException.account(movement.getAccountNumber()));
        RegisterMovementUseCase.ensureClientCanOperate(clients, account);
        Movement latest = movements.findLatestByAccountNumber(account.getAccountNumber()).orElse(null);

        account.correctLatestMovement(movement, latest, newAmount);
        accounts.save(account);
        return movements.save(movement);
    }
}
