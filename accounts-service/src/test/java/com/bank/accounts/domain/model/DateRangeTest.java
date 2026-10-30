package com.bank.accounts.domain.model;

import com.bank.accounts.domain.exception.ErrorCategory;
import com.bank.accounts.domain.exception.InvalidDateRangeException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Report range rules (F4-AC2, F4-AC4). */
class DateRangeTest {

    private static final ZoneId GUAYAQUIL = ZoneId.of("America/Guayaquil");

    @Test
    void f4Ac2CoversWholeDaysInTheConfiguredZone() {
        DateRange range = new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(range.startInclusive(GUAYAQUIL)).isEqualTo(Instant.parse("2026-10-01T05:00:00Z"));
        assertThat(range.endExclusive(GUAYAQUIL)).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
    }

    @Test
    void acceptsASingleDay() {
        LocalDate day = LocalDate.of(2026, 10, 30);
        DateRange range = new DateRange(day, day);

        assertThat(range.endExclusive(GUAYAQUIL)).isEqualTo(range.startInclusive(GUAYAQUIL).plusSeconds(86_400));
    }

    @Test
    void acceptsTheMaximumWidth() {
        LocalDate from = LocalDate.of(2024, 1, 1);

        assertThat(new DateRange(from, from.plusDays(DateRange.MAX_DAYS - 1)).from()).isEqualTo(from);
    }

    @Test
    void f4Ac4RejectsARangeWiderThanTheMaximum() {
        LocalDate from = LocalDate.of(2024, 1, 1);

        assertThatThrownBy(() -> new DateRange(from, from.plusDays(DateRange.MAX_DAYS)))
                .isInstanceOfSatisfying(InvalidDateRangeException.class, error -> {
                    assertThat(error).hasMessage("El rango de fechas no puede superar 366 días");
                    assertThat(error.code()).isEqualTo("INVALID_PARAMETER");
                    assertThat(error.category()).isEqualTo(ErrorCategory.INVALID_INPUT);
                    assertThat(error.title()).isEqualTo("Parámetro inválido");
                });
    }

    @Test
    void f4Ac4RejectsAnInvertedRange() {
        assertThatThrownBy(() -> new DateRange(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessage("La fecha inicial no puede ser posterior a la final");
    }

    @Test
    void f4Ac4RejectsMissingDates() {
        assertThatThrownBy(() -> new DateRange(null, LocalDate.of(2026, 10, 1)))
                .isInstanceOf(InvalidDateRangeException.class);
        assertThatThrownBy(() -> new DateRange(LocalDate.of(2026, 10, 1), null))
                .isInstanceOf(InvalidDateRangeException.class);
    }
}
