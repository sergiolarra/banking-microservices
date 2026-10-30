package com.bank.accounts.domain.model;

import com.bank.accounts.domain.exception.BusinessRuleException;
import com.bank.accounts.domain.exception.InactiveAccountException;
import com.bank.accounts.domain.exception.InsufficientFundsException;
import com.bank.accounts.domain.exception.MovementNotModifiableException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A bank account (SPEC §4.3). The available balance only changes through
 * {@link #registerMovement} and {@link #correctLatestMovement}, which enforce F2/F3.
 */
public final class Account {

    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("^[0-9]{6,10}$");

    private final Long id;
    private final String accountNumber;
    private final BigDecimal initialBalance;
    private final UUID clientId;
    private AccountType accountType;
    private BigDecimal availableBalance;
    private boolean active;

    private Account(Long id, String accountNumber, AccountType accountType, BigDecimal initialBalance,
                    BigDecimal availableBalance, boolean active, UUID clientId) {
        this.id = id;
        this.accountNumber = requireAccountNumber(accountNumber);
        this.accountType = requireAccountType(accountType);
        this.initialBalance = requireNonNegative(initialBalance, "Initial balance");
        this.availableBalance = requireNonNegative(availableBalance, "Available balance");
        this.active = active;
        this.clientId = Objects.requireNonNull(clientId, "clientId");
    }

    /** Opens an account; the available balance starts at the initial balance. {@code null} active means active. */
    public static Account open(String accountNumber, AccountType accountType, BigDecimal initialBalance,
                               UUID clientId, Boolean active) {
        return new Account(null, accountNumber, accountType, initialBalance, initialBalance,
                active == null || active, clientId);
    }

    public static Account restore(Long id, String accountNumber, AccountType accountType, BigDecimal initialBalance,
                                  BigDecimal availableBalance, boolean active, UUID clientId) {
        return new Account(id, accountNumber, accountType, initialBalance, availableBalance, active, clientId);
    }

    /**
     * F2: applies a deposit (positive amount) or a withdrawal (negative amount) and
     * returns the resulting ledger entry. Nothing changes if a rule fails.
     */
    public Movement registerMovement(BigDecimal amount, Instant date) {
        BigDecimal value = Money.movementAmount(amount);
        requireActive();
        BigDecimal newBalance = balanceAfter(availableBalance.add(value));
        availableBalance = newBalance;
        return Movement.record(accountNumber, date, value, newBalance);
    }

    /**
     * F2-AC8: replaces the amount of the latest movement, as if it had been registered
     * with the new amount. Correcting an older entry would invalidate every later
     * balance, so it is rejected (ADR-012).
     */
    public void correctLatestMovement(Movement movement, Movement latestOfAccount, BigDecimal newAmount) {
        if (!accountNumber.equals(movement.getAccountNumber()) || !movement.isSameEntryAs(latestOfAccount)) {
            throw new MovementNotModifiableException("Only the latest movement of the account can be modified");
        }
        BigDecimal value = Money.movementAmount(newAmount);
        requireActive();
        BigDecimal newBalance = balanceAfter(availableBalance.subtract(movement.getAmount()).add(value));
        availableBalance = newBalance;
        movement.correct(value, newBalance);
    }

    public void changeType(AccountType newType) {
        this.accountType = requireAccountType(newType);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    private void requireActive() {
        if (!active) {
            throw new InactiveAccountException("Inactive account");
        }
    }

    /** ACC-3 and ACC-5: zero is allowed, a negative balance is not. */
    private static BigDecimal balanceAfter(BigDecimal newBalance) {
        if (newBalance.signum() < 0) {
            throw new InsufficientFundsException();
        }
        return Money.requireWithinBalanceLimit(newBalance);
    }

    private static String requireAccountNumber(String accountNumber) {
        if (accountNumber == null || !ACCOUNT_NUMBER.matcher(accountNumber).matches()) {
            throw new BusinessRuleException("Account number must have 6 to 10 digits");
        }
        return accountNumber;
    }

    private static AccountType requireAccountType(AccountType accountType) {
        if (accountType == null) {
            throw new BusinessRuleException("Account type is required");
        }
        return accountType;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        BigDecimal scaled = Money.twoDecimals(value, field);
        if (scaled.signum() < 0) {
            throw new BusinessRuleException(field + " must be greater than or equal to 0");
        }
        return Money.requireWithinBalanceLimit(scaled);
    }

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public boolean isActive() {
        return active;
    }

    public UUID getClientId() {
        return clientId;
    }
}
