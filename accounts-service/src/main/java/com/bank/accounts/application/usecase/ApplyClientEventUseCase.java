package com.bank.accounts.application.usecase;

import com.bank.accounts.application.command.ClientEventCommand;
import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.model.ClientSnapshot;
import com.bank.accounts.domain.port.AccountRepository;
import com.bank.accounts.domain.port.ClientSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the local client replica up to date (ADR-008). Events may arrive repeated or
 * out of order: the snapshot ignores versions it has already applied (ADR-010). An
 * event for an unknown client creates the snapshot from its data, whatever its type.
 */
@Service
public class ApplyClientEventUseCase {

    private final ClientSnapshotRepository snapshots;
    private final AccountRepository accounts;

    public ApplyClientEventUseCase(ClientSnapshotRepository snapshots, AccountRepository accounts) {
        this.snapshots = snapshots;
        this.accounts = accounts;
    }

    @Transactional
    public void execute(ClientEventCommand event) {
        switch (event.type()) {
            case CREATED, UPDATED -> applyState(event);
            case DELETED -> applyDeletion(event);
        }
    }

    private void applyState(ClientEventCommand event) {
        snapshots.findById(event.clientId()).ifPresentOrElse(
                snapshot -> {
                    if (snapshot.update(event.name(), event.identification(), event.active(), event.version(),
                            event.occurredAt())) {
                        snapshots.save(snapshot);
                    }
                },
                () -> snapshots.save(ClientSnapshot.create(event.clientId(), event.name(), event.identification(),
                        event.active(), event.version(), event.occurredAt())));
    }

    /** ADR-016: the client's accounts are deactivated but kept, with their movements. */
    private void applyDeletion(ClientEventCommand event) {
        snapshots.findById(event.clientId()).ifPresentOrElse(
                snapshot -> {
                    if (snapshot.markDeleted(event.version(), event.occurredAt())) {
                        snapshots.save(snapshot);
                        deactivateAccountsOf(snapshot);
                    }
                },
                () -> snapshots.save(ClientSnapshot.restore(event.clientId(), event.name(), event.identification(),
                        false, true, event.version(), event.occurredAt())));
    }

    private void deactivateAccountsOf(ClientSnapshot snapshot) {
        for (Account account : accounts.findAllByClientId(snapshot.getClientId())) {
            account.deactivate();
            accounts.save(account);
        }
    }
}
