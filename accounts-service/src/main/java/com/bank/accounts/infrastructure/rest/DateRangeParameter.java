package com.bank.accounts.infrastructure.rest;

import com.bank.accounts.domain.exception.InvalidDateRangeException;
import com.bank.accounts.domain.model.DateRange;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Parses the {@code date} parameter of {@code /reports}: one value with the range
 * {@code from,to}, as the brief proposes (ADR-015, ADR-028). The range rules themselves
 * live in {@link DateRange}.
 */
final class DateRangeParameter {

    private static final String FORMAT_MESSAGE = "date must have the form yyyy-MM-dd,yyyy-MM-dd";

    private DateRangeParameter() {
    }

    static DateRange parse(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 2) {
            throw new InvalidDateRangeException(FORMAT_MESSAGE);
        }
        try {
            return new DateRange(LocalDate.parse(parts[0].trim()), LocalDate.parse(parts[1].trim()));
        } catch (DateTimeParseException e) {
            throw new InvalidDateRangeException(FORMAT_MESSAGE);
        }
    }
}
