package com.bank.accounts;

import com.bank.accounts.domain.model.Account;
import com.bank.accounts.domain.model.AccountType;
import com.bank.accounts.domain.model.ClientSnapshot;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** Shared test data. */
public final class AccountFixtures {

    public static final Instant NOW = Instant.parse("2026-10-30T15:04:05Z");
    public static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final UUID CLIENT_ID = UUID.fromString("3f6c2a8e-1b7d-4c1e-9a55-0c2f7e1d9b10");
    public static final String ACCOUNT_NUMBER = "478758";

    private AccountFixtures() {
    }

    public static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    /** A stored account opened with 2000.00 that now holds {@code balance}. */
    public static Account storedAccount(String balance) {
        return Account.restore(1L, ACCOUNT_NUMBER, AccountType.SAVINGS, money("2000.00"), money(balance), true,
                CLIENT_ID);
    }

    public static ClientSnapshot activeClient() {
        return ClientSnapshot.create(CLIENT_ID, "Jose Lema", "1712345678", true, 0, NOW);
    }

    public static ClientSnapshot inactiveClient() {
        return ClientSnapshot.create(CLIENT_ID, "Jose Lema", "1712345678", false, 1, NOW);
    }
}
