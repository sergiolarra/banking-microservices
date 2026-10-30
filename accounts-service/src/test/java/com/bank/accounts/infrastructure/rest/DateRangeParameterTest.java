package com.bank.accounts.infrastructure.rest;

import com.bank.accounts.domain.exception.InvalidDateRangeException;
import com.bank.accounts.domain.model.DateRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeParameterTest {

    @Test
    void parsesFromAndTo() {
        assertThat(DateRangeParameter.parse("2026-10-01, 2026-10-31"))
                .isEqualTo(new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-01", "2026-10-01,2026-10-31,2026-11-01", "01/10/2026,31/10/2026", ",", ""})
    void f4Ac4RejectsAMalformedRange(String value) {
        assertThatThrownBy(() -> DateRangeParameter.parse(value))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessage("date must have the form yyyy-MM-dd,yyyy-MM-dd");
    }
}
