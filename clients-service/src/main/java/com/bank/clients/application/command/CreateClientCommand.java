package com.bank.clients.application.command;

import com.bank.clients.domain.model.PersonalDetails;

/** Data to register a client. {@code active} may be {@code null} (defaults to active). */
public record CreateClientCommand(PersonalDetails details, String rawPassword, Boolean active) {
}
