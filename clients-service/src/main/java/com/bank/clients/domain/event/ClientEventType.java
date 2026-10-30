package com.bank.clients.domain.event;

/** Client events and their names in the event contract (SPEC §6). */
public enum ClientEventType {
    CREATED("client.created"),
    UPDATED("client.updated"),
    DELETED("client.deleted");

    private final String eventName;

    ClientEventType(String eventName) {
        this.eventName = eventName;
    }

    public String eventName() {
        return eventName;
    }
}
