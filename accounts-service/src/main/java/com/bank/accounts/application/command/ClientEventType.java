package com.bank.accounts.application.command;

import java.util.Arrays;

/** Client events this service understands (SPEC §6). */
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

    public static ClientEventType fromEventName(String eventName) {
        return Arrays.stream(values())
                .filter(type -> type.eventName.equals(eventName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown client event type: " + eventName));
    }
}
