package com.bank.accounts.application.usecase;

import com.bank.accounts.application.command.AccountChanges;
import com.bank.accounts.application.command.CreateAccountCommand;
import com.bank.accounts.domain.exception.ClientUnavailableException;
import com.bank.accounts.domain.exception.DuplicateResourceException;
import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.model.AccountType;
import com.bank.accounts.domain.pagination.PageQuery;
import com.bank.accounts.domain.pagination.PagedResult;
import com.bank.accounts.domain.port.AccountRepository;
import com.bank.accounts.domain.port.ClientSnapshotRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.bank.accounts.AccountFixtures.ACCOUNT_NUMBER;
import static com.bank.accounts.AccountFixtures.CLIENT_ID;
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

/** Create, update, get and list accounts. */
@ExtendWith(MockitoExtension.class)
class AccountUseCasesTest {

    @Mock
    private AccountRepository accounts;
    @Mock
    private ClientSnapshotRepository clients;

    private static final CreateAccountCommand OPEN_SAVINGS =
            new CreateAccountCommand(ACCOUNT_NUMBER, AccountType.SAVINGS, money("2000"), CLIENT_ID, null);

    @Nested
    class Create {

        @Test
        void opensAnAccountForAnActiveClient() {
            when(clients.findById(CLIENT_ID)).thenReturn(Optional.of(activeClient()));
            when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            Account account = new CreateAccountUseCase(accounts, clients).execute(OPEN_SAVINGS);

            assertThat(account.getAvailableBalance()).isEqualTo(money("2000.00"));
            assertThat(account.isActive()).isTrue();
            verify(accounts).save(account);
        }

        @Test
        void f1Ac6RejectsAClientThisServiceDoesNotKnow() {
            when(clients.findById(CLIENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> new CreateAccountUseCase(accounts, clients).execute(OPEN_SAVINGS))
                    .isInstanceOf(ClientUnavailableException.class)
                    .hasMessage("Client " + CLIENT_ID + " is not known to this service");
            verify(accounts, never()).save(any());
        }

        @Test
        void f1Ac6RejectsAnInactiveClient() {
            when(clients.findById(CLIENT_ID)).thenReturn(Optional.of(inactiveClient()));

            assertThatThrownBy(() -> new CreateAccountUseCase(accounts, clients).execute(OPEN_SAVINGS))
                    .isInstanceOf(ClientUnavailableException.class)
                    .hasMessage("Client " + CLIENT_ID + " is inactive or has been deleted");
        }

        @Test
        void rejectsADuplicateAccountNumber() {
            when(clients.findById(CLIENT_ID)).thenReturn(Optional.of(activeClient()));
            when(accounts.existsByAccountNumber(ACCOUNT_NUMBER)).thenReturn(true);

            assertThatThrownBy(() -> new CreateAccountUseCase(accounts, clients).execute(OPEN_SAVINGS))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessage("An account with number 478758 already exists");
            verify(accounts, never()).save(any());
        }
    }

    @Nested
    class Update {

        private Account locked;

        private Account update(AccountChanges changes) {
            locked = storedAccount("2000.00");
            when(accounts.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.of(locked));
            when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            return new UpdateAccountUseCase(accounts).execute(ACCOUNT_NUMBER, changes);
        }

        @Test
        void changesTypeAndDeactivatesUnderLock() {
            Account updated = update(new AccountChanges(AccountType.CHECKING, false));

            assertThat(updated.getAccountType()).isEqualTo(AccountType.CHECKING);
            assertThat(updated.isActive()).isFalse();
            verify(accounts).findByAccountNumberForUpdate(ACCOUNT_NUMBER);
        }

        @Test
        void f1Ac7KeepsBalancesAndUnchangedFields() {
            Account updated = update(new AccountChanges(null, null));

            assertThat(updated.getAccountType()).isEqualTo(AccountType.SAVINGS);
            assertThat(updated.isActive()).isTrue();
            assertThat(updated.getInitialBalance()).isEqualTo(money("2000.00"));
            assertThat(updated.getAvailableBalance()).isEqualTo(money("2000.00"));
        }

        @Test
        void reactivatesAnAccount() {
            Account inactive = storedAccount("10.00");
            inactive.deactivate();
            when(accounts.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).thenReturn(Optional.of(inactive));
            when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(new UpdateAccountUseCase(accounts).execute(ACCOUNT_NUMBER, new AccountChanges(null, true))
                    .isActive()).isTrue();
        }

        @Test
        void failsWhenTheAccountDoesNotExist() {
            when(accounts.findByAccountNumberForUpdate("999999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> new UpdateAccountUseCase(accounts).execute("999999", new AccountChanges(null, true)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("No account exists with number 999999");
        }
    }

    @Nested
    class Read {

        @Test
        void getsAnAccount() {
            Account account = storedAccount("10.00");
            when(accounts.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

            assertThat(new GetAccountUseCase(accounts).execute(ACCOUNT_NUMBER)).isSameAs(account);
        }

        @Test
        void failsWhenTheAccountDoesNotExist() {
            when(accounts.findByAccountNumber("999999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> new GetAccountUseCase(accounts).execute("999999"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void listsTheAccountsOfAClient() {
            PageQuery query = new PageQuery(0, 20);
            PagedResult<Account> page = new PagedResult<>(List.of(storedAccount("1.00")), 0, 20, 1);
            when(accounts.findAll(CLIENT_ID, query)).thenReturn(page);

            assertThat(new ListAccountsUseCase(accounts).execute(CLIENT_ID, query)).isSameAs(page);
        }
    }

    @Test
    void savesWhatTheUseCaseBuilt() {
        when(clients.findById(CLIENT_ID)).thenReturn(Optional.of(activeClient()));
        when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new CreateAccountUseCase(accounts, clients).execute(
                new CreateAccountCommand(ACCOUNT_NUMBER, AccountType.CHECKING, money("0"), CLIENT_ID, false));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(saved.getValue().isActive()).isFalse();
    }
}
