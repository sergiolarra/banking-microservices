package com.bank.accounts.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * F4 "Account statement": the accounts of a client with their balances and the
 * movements of a date range. It is built in memory from data loaded in a fixed number
 * of queries, whatever the number of accounts (F4-AC6).
 */
public record AccountStatement(ClientSnapshot client, DateRange range, Instant generatedAt, List<Entry> accounts) {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final Comparator<Movement> OLDEST_FIRST =
            Comparator.comparing(Movement::getDate).thenComparing(Movement::getId, Comparator.nullsLast(Long::compare));

    public AccountStatement {
        accounts = List.copyOf(accounts);
    }

    /**
     * @param totalCredits sum of the deposits in the range
     * @param totalDebits  sum of the withdrawals in the range, as a positive number
     * @param movements    movements in the range, oldest first (F4-AC2)
     */
    public record Entry(
            String accountNumber,
            AccountType accountType,
            boolean active,
            BigDecimal initialBalance,
            BigDecimal availableBalance,
            BigDecimal totalCredits,
            BigDecimal totalDebits,
            List<Movement> movements) {

        public Entry {
            movements = List.copyOf(movements);
        }
    }

    /**
     * @param accounts         every account of the client, in the order to show them
     * @param movementsInRange movements of those accounts within the range, in any order
     */
    public static AccountStatement of(ClientSnapshot client, DateRange range, Instant generatedAt,
                                      List<Account> accounts, List<Movement> movementsInRange) {
        Map<String, List<Movement>> byAccount = movementsInRange.stream()
                .sorted(OLDEST_FIRST)
                .collect(Collectors.groupingBy(Movement::getAccountNumber, LinkedHashMap::new, Collectors.toList()));
        List<Entry> entries = accounts.stream()
                .map(account -> entry(account, byAccount.getOrDefault(account.getAccountNumber(), List.of())))
                .toList();
        return new AccountStatement(client, range, generatedAt, entries);
    }

    private static Entry entry(Account account, List<Movement> movements) {
        return new Entry(account.getAccountNumber(), account.getAccountType(), account.isActive(),
                account.getInitialBalance(), account.getAvailableBalance(),
                sum(movements, amount -> amount.signum() > 0), sum(movements, amount -> amount.signum() < 0).abs(),
                movements);
    }

    private static BigDecimal sum(List<Movement> movements, Predicate<BigDecimal> filter) {
        return movements.stream().map(Movement::getAmount).filter(filter).reduce(ZERO, BigDecimal::add);
    }
}
