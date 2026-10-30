package com.bank.accounts.application.usecase;

import com.bank.accounts.domain.exception.ClientUnavailableException;
import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.model.Movement;
import com.bank.accounts.domain.port.AccountRepository;
import com.bank.accounts.domain.port.ClientSnapshotRepository;
import com.bank.accounts.domain.port.MovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * F2/F3: registers a deposit or withdrawal. The account row is locked for the whole
 * transaction, and the new balance and the movement are stored atomically
 * (F2-AC4, F2-AC5, ADR-013).
 */
@Service
public class RegisterMovementUseCase {

    private final AccountRepository accounts;
    private final MovementRepository movements;
    private final ClientSnapshotRepository clients;
    private final Clock clock;

    public RegisterMovementUseCase(AccountRepository accounts, MovementRepository movements,
                                   ClientSnapshotRepository clients, Clock clock) {
        this.accounts = accounts;
        this.movements = movements;
        this.clients = clients;
        this.clock = clock;
    }

    @Transactional
    public Movement execute(String accountNumber, BigDecimal amount) {
        Account account = accounts.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> ResourceNotFoundException.account(accountNumber));
        ensureClientCanOperate(clients, account);

        Movement movement = account.registerMovement(amount, now());
        accounts.save(account);
        return movements.save(movement);
    }

    /**
     * Truncated to milliseconds so the date returned now is the same one read back later
     * (PostgreSQL keeps microseconds; the clock may have nanoseconds).
     */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    /** F2-AC7: an inactive or deleted client cannot move money. */
    static void ensureClientCanOperate(ClientSnapshotRepository clients, Account account) {
        clients.findById(account.getClientId())
                .orElseThrow(() -> ClientUnavailableException.unknown(account.getClientId()))
                .ensureCanOperate();
    }
}
