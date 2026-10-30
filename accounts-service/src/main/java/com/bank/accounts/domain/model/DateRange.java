package com.bank.accounts.domain.model;

import com.bank.accounts.domain.exception.InvalidDateRangeException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Inclusive range of calendar days for the account statement (F4). Days are turned
 * into instants in the configured time zone (ADR-021).
 */
public record DateRange(LocalDate from, LocalDate to) {

    public static final int MAX_DAYS = 366;

    public DateRange {
        if (from == null || to == null) {
            throw new InvalidDateRangeException("The date range requires a start and an end date");
        }
        if (from.isAfter(to)) {
            throw new InvalidDateRangeException("The start date cannot be after the end date");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new InvalidDateRangeException("The date range cannot exceed %d days".formatted(MAX_DAYS));
        }
    }

    /** First instant of {@code from}. */
    public Instant startInclusive(ZoneId zone) {
        return from.atStartOfDay(zone).toInstant();
    }

    /** First instant of the day after {@code to}, so the whole last day is included (F4-AC2). */
    public Instant endExclusive(ZoneId zone) {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }
}
