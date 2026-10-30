package com.bank.accounts.application.usecase;

import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.AccountStatement;
import com.bank.accounts.domain.model.ClientSnapshot;
import com.bank.accounts.domain.model.DateRange;
import com.bank.accounts.domain.model.Movement;
import com.bank.accounts.domain.port.AccountRepository;
import com.bank.accounts.domain.port.ClientSnapshotRepository;
import com.bank.accounts.domain.port.MovementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static com.bank.accounts.AccountFixtures.ACCOUNT_NUMBER;
import static com.bank.accounts.AccountFixtures.CLIENT_ID;
import static com.bank.accounts.AccountFixtures.FIXED_CLOCK;
import static com.bank.accounts.AccountFixtures.NOW;
import static com.bank.accounts.AccountFixtures.activeClient;
import static com.bank.accounts.AccountFixtures.money;
import static com.bank.accounts.AccountFixtures.storedAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateAccountStatementUseCaseTest {

    private static final ZoneId GUAYAQUIL = ZoneId.of("America/Guayaquil");
    private static final DateRange OCTOBER = new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

    @Mock
    private ClientSnapshotRepository clients;
    @Mock
    private AccountRepository accounts;
    @Mock
    private MovementRepository movements;

    private GenerateAccountStatementUseCase useCase() {
        return new GenerateAccountStatementUseCase(clients, accounts, movements, FIXED_CLOCK, GUAYAQUIL);
    }

    @Test
    void f4Ac2QueriesTheWholeDaysOfTheRangeInTheConfiguredZone() {
        when(clients.findById(CLIENT_ID)).thenReturn(Optional.of(activeClient()));
        when(accounts.findAllByClientId(CLIENT_ID)).thenReturn(List.of(storedAccount("1425.00")));
        when(movements.findByClientIdBetween(CLIENT_ID, Instant.parse("2026-10-01T05:00:00Z"),
                Instant.parse("2026-11-01T05:00:00Z")))
                .thenReturn(List.of(Movement.restore(1L, ACCOUNT_NUMBER, NOW, money("-575.00"), money("1425.00"))));

        AccountStatement statement = useCase().execute(CLIENT_ID, OCTOBER);

        assertThat(statement.generatedAt()).isEqualTo(NOW);
        assertThat(statement.accounts()).hasSize(1);
        assertThat(statement.accounts().get(0).totalDebits()).isEqualTo(money("575.00"));
    }

    @Test
    void f4Ac5ADeletedClientStillHasItsStatement() {
        ClientSnapshot deleted = activeClient();
        deleted.markDeleted(1, NOW);
        when(clients.findById(CLIENT_ID)).thenReturn(Optional.of(deleted));

        assertThat(useCase().execute(CLIENT_ID, OCTOBER).client().isDeleted()).isTrue();
    }

    @Test
    void f4Ac5AnUnknownClientIsNotFound() {
        when(clients.findById(CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(CLIENT_ID, OCTOBER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No client exists with clientId " + CLIENT_ID);
        verifyNoInteractions(accounts, movements);
    }
}
