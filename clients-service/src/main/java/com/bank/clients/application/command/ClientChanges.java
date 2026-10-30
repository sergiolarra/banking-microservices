package com.bank.clients.application.command;

import com.bank.clients.domain.model.Gender;
import com.bank.clients.domain.model.PersonalDetails;

/**
 * Changes to apply to an existing client. A {@code null} field means "keep the current
 * value", which serves both PUT (every field present) and PATCH (only some fields).
 */
public record ClientChanges(
        String name,
        Gender gender,
        Integer age,
        String identification,
        String address,
        String phone,
        String rawPassword,
        Boolean active) {

    public PersonalDetails applyTo(PersonalDetails current) {
        return new PersonalDetails(
                valueOrCurrent(name, current.name()),
                valueOrCurrent(gender, current.gender()),
                valueOrCurrent(age, current.age()),
                valueOrCurrent(identification, current.identification()),
                valueOrCurrent(address, current.address()),
                valueOrCurrent(phone, current.phone()));
    }

    private static <T> T valueOrCurrent(T value, T current) {
        return value != null ? value : current;
    }
}
