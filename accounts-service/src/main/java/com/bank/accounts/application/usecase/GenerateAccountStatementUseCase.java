package com.bank.accounts.application.usecase;

import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.AccountStatement;
import com.bank.accounts.domain.model.ClientSnapshot;
import com.bank.accounts.domain.model.DateRange;
import com.bank.accounts.domain.port.AccountRepository;
import com.bank.accounts.domain.port.ClientSnapshotRepository;
import com.bank.accounts.domain.port.MovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;

/**
 * F4: builds the account statement of a client for a range of days. Three queries,
 * whatever the number of accounts: the client, its accounts and the movements in the
 * range (F4-AC6). Deleted clients keep their history (F4-AC5, ADR-016).
 */
@Service
public class GenerateAccountStatementUseCase {

    private final ClientSnapshotRepository clients;
    private final AccountRepository accounts;
    private final MovementRepository movements;
    private final Clock clock;
    private final ZoneId zone;

    public GenerateAccountStatementUseCase(ClientSnapshotRepository clients, AccountRepository accounts,
                                           MovementRepository movements, Clock clock, ZoneId statementTimeZone) {
        this.clients = clients;
        this.accounts = accounts;
        this.movements = movements;
        this.clock = clock;
        this.zone = statementTimeZone;
    }

    @Transactional(readOnly = true)
    public AccountStatement execute(UUID clientId, DateRange range) {
        ClientSnapshot client = clients.findById(clientId)
                .orElseThrow(() -> ResourceNotFoundException.client(clientId));
        return AccountStatement.of(client, range, clock.instant(),
                accounts.findAllByClientId(clientId),
                movements.findByClientIdBetween(clientId, range.startInclusive(zone), range.endExclusive(zone)));
    }
}
