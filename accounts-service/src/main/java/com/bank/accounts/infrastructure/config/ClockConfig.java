package com.bank.accounts.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Single source of time, replaceable in tests, and the zone of the statement days (ADR-021). */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ZoneId statementTimeZone(@Value("${app.time-zone:America/Guayaquil}") ZoneId zone) {
        return zone;
    }
}
