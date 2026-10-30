package com.bank.accounts.infrastructure.rest.error;

/** One invalid field or parameter in an error response (SPEC §8). */
public record FieldErrorDetail(String field, String message) {
}
