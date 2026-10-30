package com.bank.accounts.infrastructure.rest.error;

import com.bank.accounts.domain.exception.ErrorCategory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** ADR-026: every domain error category has exactly one HTTP status. */
class GlobalExceptionHandlerTest {

    @Test
    void mapsEveryCategoryToItsStatus() {
        assertThat(GlobalExceptionHandler.statusOf(ErrorCategory.INVALID_INPUT)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(GlobalExceptionHandler.statusOf(ErrorCategory.NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(GlobalExceptionHandler.statusOf(ErrorCategory.CONFLICT)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(GlobalExceptionHandler.statusOf(ErrorCategory.BUSINESS_RULE))
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
