package com.riceawa.llm.context;

public enum ChatMode {
    OFF,
    TRIGGER,
    CONTINUOUS;

    public String getName() {
        return name();
    }

    public static ChatMode fromName(String name) {
        if (name == null) return OFF;
        for (ChatMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return OFF;
    }
}
