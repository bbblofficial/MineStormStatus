package com.minestorm.status.manager;

import java.util.Locale;

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

    /** Parses "busy"/"idle"/"away" (case-insensitive, locale independent). Returns null for "clear"/unknown. */
    public static StatusType parse(String value) {
        if (value == null) return null;
        String v = value.toLowerCase(Locale.ROOT);
        for (StatusType t : values()) if (t.key.equals(v)) return t;
        return null;
    }
}
