package com.bank.clients.domain.port;

import com.bank.clients.domain.event.ClientEvent;

/**
 * Publishes client events to other services. Implementations must take part in the
 * caller's transaction, so an event exists only if the change was committed (ADR-009).
 */
public interface ClientEventPublisher {

    void publish(ClientEvent event);
}
