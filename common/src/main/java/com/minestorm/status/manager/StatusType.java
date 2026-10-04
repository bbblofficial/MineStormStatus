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

    /** Parses "busy"/"idle"/"away" (case-insensitive). Returns null for "clear"/unknown. */
    public static StatusType parse(String value) {
        if (value == null) return null;
        String v = value.toLowerCase();
        for (StatusType t : values()) if (t.key.equals(v)) return t;
        return null;
    }
}
