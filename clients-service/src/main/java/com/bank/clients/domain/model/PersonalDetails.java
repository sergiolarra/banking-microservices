package com.bank.clients.domain.model;

/**
 * Personal data shared by every {@link Person}. It is only a carrier: the
 * invariants are enforced by {@link Person} when the details are applied.
 */
public record PersonalDetails(
        String name,
        Gender gender,
        Integer age,
        String identification,
        String address,
        String phone) {
}
