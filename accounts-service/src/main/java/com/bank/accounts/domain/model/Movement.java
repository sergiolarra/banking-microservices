package com.bank.accounts.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A ledger entry of an account (SPEC §4.4). Only {@link Account} creates or corrects
 * movements, so the account balance and the movement balance never diverge.
 */
public final class Movement {

    private final Long id;
    private final String accountNumber;
    private final Instant date;
    private MovementType movementType;
    private BigDecimal amount;
    private BigDecimal balance;

    private Movement(Long id, String accountNumber, Instant date, BigDecimal amount, BigDecimal balance) {
        this.id = id;
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber");
        this.date = Objects.requireNonNull(date, "date");
        this.movementType = MovementType.of(amount);
        this.amount = amount;
        this.balance = balance;
    }

    static Movement record(String accountNumber, Instant date, BigDecimal amount, BigDecimal balance) {
        return new Movement(null, accountNumber, date, amount, balance);
    }

    /** Rebuilds a stored movement; the type is derived from the amount again (ADR-012). */
    public static Movement restore(Long id, String accountNumber, Instant date, BigDecimal amount, BigDecimal balance) {
        return new Movement(id, accountNumber, date, amount, balance);
    }

    /** Keeps the original date: a correction replaces the entry, it is not a new one. */
    void correct(BigDecimal newAmount, BigDecimal newBalance) {
        this.movementType = MovementType.of(newAmount);
        this.amount = newAmount;
        this.balance = newBalance;
    }

    boolean isSameEntryAs(Movement other) {
        return id != null && other != null && id.equals(other.id);
    }

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public Instant getDate() {
        return date;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
