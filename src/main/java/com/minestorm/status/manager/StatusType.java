package com.minestorm.status.manager;

public enum StatusType {
    BUSY("busy"),
    IDLE("idle"),
    AWAY("away");

    private final String key;

    StatusType(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
