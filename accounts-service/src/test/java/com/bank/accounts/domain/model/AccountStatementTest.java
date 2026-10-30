package com.bank.accounts.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** F4: grouping, ordering and totals of the account statement. */
class AccountStatementTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final Instant T1 = Instant.parse("2026-10-10T14:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-11T09:30:00Z");
    private static final DateRange OCTOBER = new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    private static final ClientSnapshot CLIENT =
            ClientSnapshot.create(CLIENT_ID, "Jose Lema", "1712345678", true, 0, T1);

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static Account account(String number, String available) {
        return Account.restore(1L, number, AccountType.SAVINGS, money("2000.00"), money(available), true, CLIENT_ID);
    }

    private static Movement movement(long id, String accountNumber, Instant date, String amount, String balance) {
        return Movement.restore(id, accountNumber, date, money(amount), money(balance));
    }

    @Test
    void f4Ac1And2GroupsMovementsByAccountOldestFirstWithTotals() {
        List<Movement> unordered = List.of(
                movement(3, "478758", T2, "-575.00", "2025.00"),
                movement(5, "225487", T1, "100.00", "200.00"),
                movement(2, "478758", T1, "600.00", "2600.00"),
                movement(4, "478758", T2, "-100.50", "1924.50"));

        AccountStatement statement = AccountStatement.of(CLIENT, OCTOBER, T2,
                List.of(account("478758", "1924.50"), account("225487", "200.00")), unordered);

        assertThat(statement.client()).isSameAs(CLIENT);
        assertThat(statement.range()).isEqualTo(OCTOBER);
        assertThat(statement.generatedAt()).isEqualTo(T2);
        AccountStatement.Entry first = statement.accounts().get(0);
        assertThat(first.accountNumber()).isEqualTo("478758");
        assertThat(first.availableBalance()).isEqualTo(money("1924.50"));
        assertThat(first.initialBalance()).isEqualTo(money("2000.00"));
        assertThat(first.movements()).extracting(Movement::getId).containsExactly(2L, 3L, 4L);
        assertThat(first.totalCredits()).isEqualTo(money("600.00"));
        assertThat(first.totalDebits()).isEqualTo(money("675.50"));
        assertThat(statement.accounts().get(1).movements()).extracting(Movement::getId).containsExactly(5L);
    }

    @Test
    void f4Ac3AnAccountWithoutMovementsHasZeroTotalsAndAnEmptyList() {
        AccountStatement statement = AccountStatement.of(CLIENT, OCTOBER, T2, List.of(account("478758", "2000.00")),
                List.of());

        AccountStatement.Entry entry = statement.accounts().get(0);
        assertThat(entry.movements()).isEmpty();
        assertThat(entry.totalCredits()).isEqualTo(money("0.00"));
        assertThat(entry.totalDebits()).isEqualTo(money("0.00"));
        assertThat(entry.accountType()).isEqualTo(AccountType.SAVINGS);
        assertThat(entry.active()).isTrue();
    }

    @Test
    void f4Ac3AClientWithoutAccountsHasAnEmptyStatement() {
        assertThat(AccountStatement.of(CLIENT, OCTOBER, T2, List.of(), List.of()).accounts()).isEmpty();
    }

    @Test
    void ordersMovementsWithTheSameDateById() {
        AccountStatement statement = AccountStatement.of(CLIENT, OCTOBER, T2, List.of(account("478758", "1.00")),
                List.of(movement(9, "478758", T1, "1.00", "3.00"), movement(8, "478758", T1, "1.00", "2.00")));

        assertThat(statement.accounts().get(0).movements()).extracting(Movement::getId).containsExactly(8L, 9L);
    }
}
