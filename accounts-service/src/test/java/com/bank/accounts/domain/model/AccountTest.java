package com.bank.accounts.domain.model;

import com.bank.accounts.domain.exception.BusinessRuleException;
import com.bank.accounts.domain.exception.ErrorCategory;
import com.bank.accounts.domain.exception.InactiveAccountException;
import com.bank.accounts.domain.exception.InsufficientFundsException;
import com.bank.accounts.domain.exception.MovementNotModifiableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests of the Account domain entity, covering ACC-1 to ACC-5 (SPEC §4.3) and F2/F3. */
class AccountTest {

    private static final Instant NOW = Instant.parse("2026-10-30T15:04:05Z");
    private static final UUID CLIENT_ID = UUID.fromString("3f6c2a8e-1b7d-4c1e-9a55-0c2f7e1d9b10");

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static Account accountWithBalance(String balance) {
        return Account.restore(1L, "478758", AccountType.SAVINGS, money("2000.00"), money(balance), true, CLIENT_ID);
    }

    @Nested
    @DisplayName("opening an account")
    class Opening {

        @Test
        void startsWithTheInitialBalanceAndActive() {
            Account account = Account.open("478758", AccountType.SAVINGS, money("2000"), CLIENT_ID, null);

            assertThat(account.getId()).isNull();
            assertThat(account.getAccountNumber()).isEqualTo("478758");
            assertThat(account.getAccountType()).isEqualTo(AccountType.SAVINGS);
            assertThat(account.getInitialBalance()).isEqualTo(money("2000.00"));
            assertThat(account.getAvailableBalance()).isEqualTo(money("2000.00"));
            assertThat(account.getClientId()).isEqualTo(CLIENT_ID);
            assertThat(account.isActive()).isTrue();
        }

        @Test
        void keepsAnExplicitInactiveState() {
            assertThat(Account.open("478758", AccountType.CHECKING, BigDecimal.ZERO, CLIENT_ID, false).isActive())
                    .isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"12345", "12345678901", "47875A"})
        void rejectsAnInvalidAccountNumber(String accountNumber) {
            assertThatThrownBy(() -> Account.open(accountNumber, AccountType.SAVINGS, BigDecimal.TEN, CLIENT_ID, true))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("El número de cuenta debe tener entre 6 y 10 dígitos");
        }

        @Test
        void rejectsAMissingAccountNumberOrType() {
            assertThatThrownBy(() -> Account.open(null, AccountType.SAVINGS, BigDecimal.TEN, CLIENT_ID, true))
                    .isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> Account.open("478758", null, BigDecimal.TEN, CLIENT_ID, true))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("El tipo de cuenta es obligatorio");
        }

        @Test
        void rejectsANegativeMissingOrTooPreciseInitialBalance() {
            assertThatThrownBy(() -> Account.open("478758", AccountType.SAVINGS, money("-0.01"), CLIENT_ID, true))
                    .hasMessage("El saldo inicial debe ser mayor o igual a 0");
            assertThatThrownBy(() -> Account.open("478758", AccountType.SAVINGS, null, CLIENT_ID, true))
                    .hasMessage("El saldo inicial es obligatorio");
            assertThatThrownBy(() -> Account.open("478758", AccountType.SAVINGS, money("1.001"), CLIENT_ID, true))
                    .hasMessage("El saldo inicial admite como máximo 2 decimales");
        }

        @Test
        void rejectsAnInitialBalanceThatDoesNotFitTheColumn() {
            assertThatThrownBy(() -> Account.open("478758", AccountType.SAVINGS, money("10000000000000.00"),
                    CLIENT_ID, true))
                    .hasMessage("El saldo excede el máximo permitido");
        }

        @Test
        void requiresAClient() {
            assertThatThrownBy(() -> Account.open("478758", AccountType.SAVINGS, BigDecimal.TEN, null, true))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("ACC-1 the amount cannot be zero")
    class ZeroAmount {

        @ParameterizedTest
        @ValueSource(strings = {"0", "0.00", "-0.00"})
        void rejectsAZeroAmount(String amount) {
            Account account = accountWithBalance("2000.00");

            assertThatThrownBy(() -> account.registerMovement(money(amount), NOW))
                    .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                        assertThat(error).hasMessage("El valor del movimiento no puede ser cero");
                        assertThat(error.code()).isEqualTo("BUSINESS_RULE_VIOLATION");
                        assertThat(error.category()).isEqualTo(ErrorCategory.BUSINESS_RULE);
                        assertThat(error.title()).isEqualTo("Regla de negocio incumplida");
                    });
            assertThat(account.getAvailableBalance()).isEqualTo(money("2000.00"));
        }

        @Test
        void rejectsAmountsOutsideTheMovementLimits() {
            Account account = accountWithBalance("2000.00");

            assertThatThrownBy(() -> account.registerMovement(null, NOW))
                    .hasMessage("El valor del movimiento es obligatorio");
            assertThatThrownBy(() -> account.registerMovement(money("10.555"), NOW))
                    .hasMessage("El valor del movimiento admite como máximo 2 decimales");
            assertThatThrownBy(() -> account.registerMovement(money("1000000.01"), NOW))
                    .hasMessage("El valor del movimiento no puede superar 1000000.00 en valor absoluto");
            assertThatThrownBy(() -> account.registerMovement(money("-1000000.01"), NOW))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void acceptsTheMaximumAmount() {
            Account account = accountWithBalance("0.00");

            assertThat(account.registerMovement(money("1000000.00"), NOW).getBalance()).isEqualTo(money("1000000.00"));
        }
    }

    @Nested
    @DisplayName("ACC-2 an inactive account rejects movements")
    class InactiveAccount {

        @Test
        void rejectsMovementsOnAnInactiveAccount() {
            Account account = accountWithBalance("2000.00");
            account.deactivate();

            assertThatThrownBy(() -> account.registerMovement(money("100"), NOW))
                    .isInstanceOfSatisfying(InactiveAccountException.class, error -> {
                        assertThat(error).hasMessage("Cuenta inactiva");
                        assertThat(error.code()).isEqualTo("INACTIVE_ACCOUNT");
                        assertThat(error.category()).isEqualTo(ErrorCategory.BUSINESS_RULE);
                        assertThat(error.title()).isEqualTo("Cuenta inactiva");
                    });
            assertThat(account.getAvailableBalance()).isEqualTo(money("2000.00"));
        }

        @Test
        void acceptsMovementsAgainOnceReactivated() {
            Account account = accountWithBalance("2000.00");
            account.deactivate();
            account.activate();

            assertThat(account.registerMovement(money("1"), NOW).getBalance()).isEqualTo(money("2001.00"));
        }
    }

    @Nested
    @DisplayName("ACC-3 / F3 a withdrawal cannot exceed the available balance")
    class InsufficientFunds {

        @Test
        void f3Ac1RejectsAWithdrawalAboveTheBalanceWithTheRequiredMessage() {
            Account account = accountWithBalance("100.00");

            assertThatThrownBy(() -> account.registerMovement(money("-150.00"), NOW))
                    .isInstanceOfSatisfying(InsufficientFundsException.class, error -> {
                        assertThat(error).hasMessage("Saldo no disponible");
                        assertThat(error.code()).isEqualTo("INSUFFICIENT_FUNDS");
                        assertThat(error.category()).isEqualTo(ErrorCategory.BUSINESS_RULE);
                        assertThat(error.title()).isEqualTo("Saldo no disponible");
                    });
        }

        @Test
        void f3Ac2LeavesTheBalanceUnchanged() {
            Account account = accountWithBalance("100.00");

            assertThatThrownBy(() -> account.registerMovement(money("-100.01"), NOW))
                    .isInstanceOf(InsufficientFundsException.class);
            assertThat(account.getAvailableBalance()).isEqualTo(money("100.00"));
        }

        @Test
        void f3Ac3RejectsAnyWithdrawalFromAnEmptyAccount() {
            assertThatThrownBy(() -> accountWithBalance("0.00").registerMovement(money("-0.01"), NOW))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        void rejectsADepositThatWouldOverflowTheBalance() {
            Account account = accountWithBalance("9999999999999.00");

            assertThatThrownBy(() -> account.registerMovement(money("1.00"), NOW))
                    .hasMessage("El saldo excede el máximo permitido");
        }
    }

    @Nested
    @DisplayName("ACC-4 / F2 a valid movement updates the balance and is recorded")
    class ValidMovement {

        @Test
        void f2Ac1RegistersADeposit() {
            Account account = accountWithBalance("2000.00");

            Movement movement = account.registerMovement(money("600"), NOW);

            assertThat(movement.getMovementType()).isEqualTo(MovementType.DEPOSIT);
            assertThat(movement.getAmount()).isEqualTo(money("600.00"));
            assertThat(movement.getBalance()).isEqualTo(money("2600.00"));
            assertThat(movement.getDate()).isEqualTo(NOW);
            assertThat(movement.getAccountNumber()).isEqualTo("478758");
            assertThat(movement.getId()).isNull();
            assertThat(account.getAvailableBalance()).isEqualTo(money("2600.00"));
        }

        @Test
        void f2Ac2RegistersAWithdrawal() {
            Account account = accountWithBalance("2000.00");

            Movement movement = account.registerMovement(money("-575"), NOW);

            assertThat(movement.getMovementType()).isEqualTo(MovementType.WITHDRAWAL);
            assertThat(movement.getAmount()).isEqualTo(money("-575.00"));
            assertThat(movement.getBalance()).isEqualTo(money("1425.00"));
            assertThat(account.getAvailableBalance()).isEqualTo(money("1425.00"));
        }

        @Test
        void accumulatesConsecutiveMovements() {
            Account account = accountWithBalance("2000.00");

            account.registerMovement(money("600"), NOW);
            Movement last = account.registerMovement(money("-1000.50"), NOW);

            assertThat(last.getBalance()).isEqualTo(money("1599.50"));
        }
    }

    @Nested
    @DisplayName("ACC-5 a withdrawal may leave the balance at exactly zero")
    class ZeroBalance {

        @Test
        void allowsWithdrawingTheWholeBalance() {
            Account account = accountWithBalance("540.00");

            assertThat(account.registerMovement(money("-540"), NOW).getBalance()).isEqualByComparingTo("0");
            assertThat(account.getAvailableBalance()).isEqualTo(money("0.00"));
        }
    }

    @Nested
    @DisplayName("F2-AC8 only the latest movement can be corrected")
    class LatestMovementCorrection {

        private Movement stored(long id, String amount, String balance) {
            return Movement.restore(id, "478758", NOW, money(amount), money(balance));
        }

        @ParameterizedTest
        @CsvSource({
                "-575.00, -100.00, 1900.00, WITHDRAWAL",
                "-575.00, 300.00, 2300.00, DEPOSIT",
                "600.00, -2000.00, 0.00, WITHDRAWAL"})
        void revertsTheLatestAmountAndAppliesTheNewOne(String oldAmount, String newAmount, String expectedBalance,
                                                       MovementType expectedType) {
            BigDecimal before = money("2000.00").add(money(oldAmount));
            Account account = Account.restore(1L, "478758", AccountType.SAVINGS, money("2000.00"), before, true,
                    CLIENT_ID);
            Movement latest = stored(9, oldAmount, before.toPlainString());

            account.correctLatestMovement(latest, stored(9, oldAmount, before.toPlainString()), money(newAmount));

            assertThat(account.getAvailableBalance()).isEqualTo(money(expectedBalance));
            assertThat(latest.getAmount()).isEqualTo(money(newAmount));
            assertThat(latest.getBalance()).isEqualTo(money(expectedBalance));
            assertThat(latest.getMovementType()).isEqualTo(expectedType);
            assertThat(latest.getDate()).isEqualTo(NOW);
        }

        @Test
        void rejectsCorrectingAnOlderMovement() {
            Account account = accountWithBalance("1425.00");

            assertThatThrownBy(() -> account.correctLatestMovement(stored(8, "600.00", "2600.00"),
                    stored(9, "-1175.00", "1425.00"), money("100")))
                    .isInstanceOfSatisfying(MovementNotModifiableException.class, error -> {
                        assertThat(error).hasMessage("Solo se puede modificar el último movimiento de la cuenta");
                        assertThat(error.code()).isEqualTo("MOVEMENT_NOT_MODIFIABLE");
                        assertThat(error.category()).isEqualTo(ErrorCategory.CONFLICT);
                        assertThat(error.title()).isEqualTo("Movimiento no modificable");
                    });
        }

        @Test
        void rejectsAMovementOfAnotherAccount() {
            Account account = accountWithBalance("1425.00");
            Movement foreign = Movement.restore(9L, "999999", NOW, money("-575.00"), money("1425.00"));

            assertThatThrownBy(() -> account.correctLatestMovement(foreign, foreign, money("100")))
                    .isInstanceOf(MovementNotModifiableException.class);
        }

        @Test
        void rejectsWhenTheAccountHasNoMovementToCompareWith() {
            Account account = accountWithBalance("1425.00");

            assertThatThrownBy(() -> account.correctLatestMovement(stored(9, "-575.00", "1425.00"), null,
                    money("100")))
                    .isInstanceOf(MovementNotModifiableException.class);
        }

        @Test
        void appliesTheSameFundsRuleAndChangesNothingOnFailure() {
            Account account = accountWithBalance("1425.00");
            Movement latest = stored(9, "-575.00", "1425.00");

            assertThatThrownBy(() -> account.correctLatestMovement(latest, latest, money("-2000.01")))
                    .isInstanceOf(InsufficientFundsException.class);
            assertThat(account.getAvailableBalance()).isEqualTo(money("1425.00"));
            assertThat(latest.getAmount()).isEqualTo(money("-575.00"));
        }

        @Test
        void rejectsAZeroCorrectionAndInactiveAccounts() {
            Account account = accountWithBalance("1425.00");
            Movement latest = stored(9, "-575.00", "1425.00");

            assertThatThrownBy(() -> account.correctLatestMovement(latest, latest, BigDecimal.ZERO))
                    .isInstanceOf(BusinessRuleException.class);
            account.deactivate();
            assertThatThrownBy(() -> account.correctLatestMovement(latest, latest, money("1")))
                    .isInstanceOf(InactiveAccountException.class);
        }

        @Test
        void anUnsavedMovementIsNeverTheLatestStoredEntry() {
            Account account = accountWithBalance("2000.00");
            Movement unsaved = account.registerMovement(money("10"), NOW);

            assertThatThrownBy(() -> account.correctLatestMovement(unsaved, unsaved, money("20")))
                    .isInstanceOf(MovementNotModifiableException.class);
        }
    }

    @Test
    void changesTheAccountType() {
        Account account = accountWithBalance("10.00");

        account.changeType(AccountType.CHECKING);

        assertThat(account.getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThatThrownBy(() -> account.changeType(null)).isInstanceOf(BusinessRuleException.class);
    }
}
