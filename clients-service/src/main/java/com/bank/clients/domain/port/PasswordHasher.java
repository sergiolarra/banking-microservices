package com.bank.clients.domain.port;

/** Hashes raw passwords; implemented with BCrypt in infrastructure (ADR-017). */
public interface PasswordHasher {

    String hash(String rawPassword);
}
