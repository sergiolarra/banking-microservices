package com.bank.accounts.application.usecase;

import com.bank.accounts.application.command.ClientEventCommand;
import com.bank.accounts.application.command.ClientEventType;
import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.model.ClientSnapshot;
import com.bank.accounts.domain.port.AccountRepository;
import com.bank.accounts.domain.port.ClientSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.bank.accounts.AccountFixtures.CLIENT_ID;
import static com.bank.accounts.AccountFixtures.NOW;
import static com.bank.accounts.AccountFixtures.activeClient;
import static com.bank.accounts.AccountFixtures.storedAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Replica maintenance from client events (ADR-008, ADR-010, ADR-016). */
@ExtendWith(MockitoExtension.class)
class ApplyClientEventUseCaseTest {

    private static final Instant LATER = NOW.plusSeconds(60);

    @Mock
    private ClientSnapshotRepository snapshots;
    @Mock
    private AccountRepository accounts;

    private ApplyClientEventUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ApplyClientEventUseCase(snapshots, accounts);
    }

    private static ClientEventCommand event(ClientEventType type, boolean active, long version) {
        return new ClientEventCommand(type, CLIENT_ID, "Jose A. Lema", "1712345678", active, version, LATER);
    }

    private ClientSnapshot saved() {
        ArgumentCaptor<ClientSnapshot> captor = ArgumentCaptor.forClass(ClientSnapshot.class);
        verify(snapshots).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void createsTheSnapshotOfANewClient() {
        when(snapshots.findById(CLIENT_ID)).thenReturn(Optional.empty());

        useCase.execute(event(ClientEventType.CREATED, true, 0));

        ClientSnapshot snapshot = saved();
        assertThat(snapshot.getName()).isEqualTo("Jose A. Lema");
        assertThat(snapshot.canOperate()).isTrue();
        assertThat(snapshot.getVersion()).isZero();
    }

    @Test
    void appliesANewerUpdate() {
        when(snapshots.findById(CLIENT_ID)).thenReturn(Optional.of(activeClient()));

        useCase.execute(event(ClientEventType.UPDATED, false, 1));

        assertThat(saved().isActive()).isFalse();
    }

    @Test
    void ignoresAnOlderOrRepeatedUpdate() {
        ClientSnapshot current = ClientSnapshot.create(CLIENT_ID, "Jose Lema", "1712345678", true, 5, NOW);
        when(snapshots.findById(CLIENT_ID)).thenReturn(Optional.of(current));

        useCase.execute(event(ClientEventType.UPDATED, false, 5));

        verify(snapshots, never()).save(any());
    }

    @Test
    void anUpdateForAnUnknownClientCreatesItsSnapshot() {
        when(snapshots.findById(CLIENT_ID)).thenReturn(Optional.empty());

        useCase.execute(event(ClientEventType.UPDATED, true, 3));

        assertThat(saved().getVersion()).isEqualTo(3);
    }

    @Test
    void adr016DeletionDeactivatesTheAccountsButKeepsThem() {
        Account first = storedAccount("10.00");
        Account second = storedAccount("0.00");
        when(snapshots.findById(CLIENT_ID)).thenReturn(Optional.of(activeClient()));
        when(accounts.findAllByClientId(CLIENT_ID)).thenReturn(List.of(first, second));

        useCase.execute(event(ClientEventType.DELETED, true, 1));

        assertThat(saved().isDeleted()).isTrue();
        assertThat(first.isActive()).isFalse();
        assertThat(second.isActive()).isFalse();
        verify(accounts, times(2)).save(any());
    }

    @Test
    void ignoresAnOutdatedDeletion() {
        ClientSnapshot current = ClientSnapshot.create(CLIENT_ID, "Jose Lema", "1712345678", true, 5, NOW);
        when(snapshots.findById(CLIENT_ID)).thenReturn(Optional.of(current));

        useCase.execute(event(ClientEventType.DELETED, false, 4));

        verify(snapshots, never()).save(any());
        verifyNoInteractions(accounts);
    }

    @Test
    void aDeletionForAnUnknownClientLeavesATombstone() {
        when(snapshots.findById(CLIENT_ID)).thenReturn(Optional.empty());

        useCase.execute(event(ClientEventType.DELETED, true, 7));

        ClientSnapshot tombstone = saved();
        assertThat(tombstone.isDeleted()).isTrue();
        assertThat(tombstone.isActive()).isFalse();
        assertThat(tombstone.getVersion()).isEqualTo(7);
        verifyNoInteractions(accounts);
    }
}
