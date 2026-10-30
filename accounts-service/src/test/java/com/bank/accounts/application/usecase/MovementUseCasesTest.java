package com.bank.accounts.application.usecase;

import com.bank.accounts.domain.exception.ClientUnavailableException;
import com.bank.accounts.domain.exception.InsufficientFundsException;
import com.bank.accounts.domain.exception.MovementNotModifiableException;
import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.model.Movement;
import com.bank.accounts.domain.model.MovementType;
import com.bank.accounts.domain.pagination.PageQuery;
import com.bank.accounts.domain.pagination.PagedResult;
import com.bank.accounts.domain.port.AccountRepository;
import com.bank.accounts.domain.port.ClientSnapshotRepository;
import com.bank.accounts.domain.port.MovementRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.bank.accounts.AccountFixtures.ACCOUNT_NUMBER;
import static com.bank.accounts.AccountFixtures.CLIENT_ID;
import static com.bank.accounts.AccountFixtures.FIXED_CLOCK;
import static com.bank.accounts.AccountFixtures.NOW;
import static com.bank.accounts.AccountFixtures.activeClient;
import static com.bank.accounts.AccountFixtures.inactiveClient;
import static com.bank.accounts.AccountFixtures.money;
import static com.bank.accounts.AccountFixtures.storedAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Register, correct, get and list movements. */
@ExtendWith(MockitoExtension.class)
class MovementUseCasesTest {

    @Mock
    private AccountRepository accounts;
    @Mock
    private MovementRepository movements;
    @Mock
    private ClientSnapshotRepository clients;

    private RegisterMovementUseCase register() {
        return new RegisterMovementUseCase(accounts, movements, clients, FIXED_CLOCK);
    }

    private CorrectMovementUseCase correct() {
        return new CorrectMovementUseCase(accounts, movements, clients);
    }

    private void givenLockedAccount(Account account) {
        when(accounts.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));
    }

    @Nested
    class Register {

        @Test
        void f2Ac1StoresTheNewBalanceAndTheMovementUnderLock() {
            Account account = storedAccount("2000.00");
            givenLockedAccount(account);
            when(clients.findById(CLIENT_ID)).thenReturn(Optional.of(activeClient()));
            when(movements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            Movement movement = register().execute(ACCOUNT_NUMBER, money("600"));

            assertThat(movement.getMovementType()).isEqualTo(MovementType.DEPOSIT);
            assertThat(movement.getBalance()).isEqualTo(money("2600.00"));
            assertThat(movement.getDate()).isEqualTo(NOW);
            ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
            verify(accounts).save(saved.capture());
            assertThat(saved.getValue().getAvailableBalance()).isEqualTo(money("2600.00"));
        }

        @Test
        void recordsTheDateWithMillisecondPrecision() {
            givenLockedAccount(storedAccount("2000.00"));
            when(clients.findById(CLIENT_ID)).thenReturn(Optional.of(activeClient()));
            when(movements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            Clock nanoClock = Clock.fixed(Instant.parse("2026-10-30T16:14:00.315324038Z"), ZoneOffset.UTC);

            Movement movement = new RegisterMovementUseCase(accounts, movements, clients, nanoClock)
                    .execute(ACCOUNT_NUMBER, money("1"));

            assertThat(movement.getDate()).isEqualTo(Instant.parse("2026-10-30T16:14:00.315Z"));
        }

        @Test
        void f3Ac2StoresNothingWhenFundsAreInsufficient() {
            givenLockedAccount(storedAccount("100.00"));
            when(clients.findById(CLIENT_ID)).thenReturn(Optional.of(activeClient()));

            assertThatThrownBy(() -> register().execute(ACCOUNT_NUMBER, money("-150")))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessage("Insufficient funds");
            verify(accounts, never()).save(any());
            verify(movements, never()).save(any());
        }

        @Test
        void f2Ac7RejectsAnInactiveClient() {
            givenLockedAccount(storedAccount("100.00"));
            when(clients.findById(CLIENT_ID)).thenReturn(Optional.of(inactiveClient()));

            assertThatThrownBy(() -> register().execute(ACCOUNT_NUMBER, money("10")))
                    .isInstanceOf(ClientUnavailableException.class);
            verify(movements, never()).save(any());
        }

        @Test
        void rejectsAnAccountWhoseClientIsUnknown() {
            givenLockedAccount(storedAccount("100.00"));
            when(clients.findById(CLIENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> register().execute(ACCOUNT_NUMBER, money("10")))
                    .isInstanceOf(ClientUnavailableException.class);
        }

        @Test
        void failsWhenTheAccountDoesNotExist() {
            when(accounts.findByAccountNumberForUpdate("999999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> register().execute("999999", money("10")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Correct {

        private final Movement latest = Movement.restore(9L, ACCOUNT_NUMBER, NOW, money("-575.00"), money("1425.00"));

        @Test
        void f2Ac8CorrectsTheLatestMovement() {
            when(movements.findById(9L)).thenReturn(Optional.of(latest));
            givenLockedAccount(storedAccount("1425.00"));
            when(clients.findById(CLIENT_ID)).thenReturn(Optional.of(activeClient()));
            when(movements.findLatestByAccountNumber(ACCOUNT_NUMBER))
                    .thenReturn(Optional.of(Movement.restore(9L, ACCOUNT_NUMBER, NOW, money("-575.00"), money("1425.00"))));
            when(movements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            Movement corrected = correct().execute(9L, money("-100"));

            assertThat(corrected.getBalance()).isEqualTo(money("1900.00"));
            ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
            verify(accounts).save(saved.capture());
            assertThat(saved.getValue().getAvailableBalance()).isEqualTo(money("1900.00"));
        }

        @Test
        void f2Ac8RejectsAMovementThatIsNotTheLatest() {
            when(movements.findById(9L)).thenReturn(Optional.of(latest));
            givenLockedAccount(storedAccount("2025.00"));
            when(clients.findById(CLIENT_ID)).thenReturn(Optional.of(activeClient()));
            when(movements.findLatestByAccountNumber(ACCOUNT_NUMBER))
                    .thenReturn(Optional.of(Movement.restore(10L, ACCOUNT_NUMBER, NOW, money("600.00"), money("2025.00"))));

            assertThatThrownBy(() -> correct().execute(9L, money("-100")))
                    .isInstanceOf(MovementNotModifiableException.class);
            verify(movements, never()).save(any());
        }

        @Test
        void failsWhenTheMovementDoesNotExist() {
            when(movements.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> correct().execute(404L, money("1")))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("No movement exists with id 404");
        }

        @Test
        void failsWhenTheAccountOfTheMovementIsGone() {
            when(movements.findById(9L)).thenReturn(Optional.of(latest));
            when(accounts.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> correct().execute(9L, money("1")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Read {

        @Test
        void getsAMovement() {
            Movement movement = Movement.restore(1L, ACCOUNT_NUMBER, NOW, money("1.00"), money("1.00"));
            when(movements.findById(1L)).thenReturn(Optional.of(movement));

            assertThat(new GetMovementUseCase(movements).execute(1L)).isSameAs(movement);
        }

        @Test
        void failsWhenTheMovementDoesNotExist() {
            when(movements.findById(2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> new GetMovementUseCase(movements).execute(2L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void listsTheMovementsOfAnAccount() {
            PageQuery query = new PageQuery(0, 20);
            PagedResult<Movement> page = new PagedResult<>(List.of(), 0, 20, 0);
            when(movements.findAll(ACCOUNT_NUMBER, query)).thenReturn(page);

            assertThat(new ListMovementsUseCase(movements).execute(ACCOUNT_NUMBER, query)).isSameAs(page);
        }
    }
}
