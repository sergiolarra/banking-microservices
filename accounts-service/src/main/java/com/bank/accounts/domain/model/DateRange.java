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
            throw new InvalidDateRangeException("El rango de fechas requiere fecha inicial y final");
        }
        if (from.isAfter(to)) {
            throw new InvalidDateRangeException("La fecha inicial no puede ser posterior a la final");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new InvalidDateRangeException("El rango de fechas no puede superar %d días".formatted(MAX_DAYS));
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
