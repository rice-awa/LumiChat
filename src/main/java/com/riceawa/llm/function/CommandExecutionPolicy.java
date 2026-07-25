package com.riceawa.llm.function;

import java.util.Locale;
import java.util.Set;

/**
 * Policy for LLM-initiated command execution. Commands are enabled by default;
 * only commands whose root appears in the blocklist are rejected.
 */
public final class CommandExecutionPolicy {
    public static final int DEFAULT_MAX_COMMAND_LENGTH = 256;

    private CommandExecutionPolicy() {
    }

    /**
     * Evaluates a command against the default maximum command length.
     */
    public static Decision evaluate(String command, boolean operator, boolean enabled,
                                    Set<String> blocklist) {
        return evaluate(command, operator, enabled, blocklist, DEFAULT_MAX_COMMAND_LENGTH);
    }

    /**
     * Evaluates one Brigadier command input. Commands must be explicitly enabled,
     * initiated by an operator, and must not have a blocklisted top-level command root.
     */
    public static Decision evaluate(String command, boolean operator, boolean enabled,
                                    Set<String> blocklist, int maxLength) {
        if (!enabled) {
            return new Decision(false, "", "disabled");
        }
        if (!operator) {
            return new Decision(false, "", "not_operator");
        }

        if (command != null && command.length() > normalizeMaxLength(maxLength)) {
            return new Decision(false, "", "too_long");
        }

        String normalizedCommand = normalizeCommand(command);
        if (normalizedCommand == null) {
            return new Decision(false, "", "invalid_command");
        }
        if (normalizedCommand.length() > normalizeMaxLength(maxLength)) {
            return new Decision(false, commandRoot(normalizedCommand), "too_long");
        }

        String root = commandRoot(normalizedCommand);
        if (root.isEmpty()) {
            return new Decision(false, "", "invalid_command");
        }

        if (blocklist != null && !blocklist.isEmpty()) {
            for (String blockedCommand : blocklist) {
                String blockedRoot = normalizeBlocklistRoot(blockedCommand);
                if (root.equals(blockedRoot)) {
                    return new Decision(false, root, "blocked");
                }
            }
        }

        return new Decision(true, root, "allowed");
    }

    /**
     * Removes accepted surrounding whitespace and a single leading slash.
     * Returns null when input cannot be a single Brigadier command input.
     */
    public static String normalizeCommand(String command) {
        if (command == null || command.indexOf('\0') >= 0 || command.indexOf(';') >= 0
                || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
            return null;
        }

        String normalized = command.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static int normalizeMaxLength(int maxLength) {
        return Math.max(1, Math.min(DEFAULT_MAX_COMMAND_LENGTH, maxLength));
    }

    private static String normalizeBlocklistRoot(String command) {
        if (command == null) {
            return "";
        }
        String normalized = normalizeCommand(command);
        return normalized == null ? "" : commandRoot(normalized);
    }

    private static String commandRoot(String normalizedCommand) {
        int separatorIndex = 0;
        while (separatorIndex < normalizedCommand.length()
                && !Character.isWhitespace(normalizedCommand.charAt(separatorIndex))) {
            separatorIndex++;
        }
        return normalizedCommand.substring(0, separatorIndex).toLowerCase(Locale.ROOT);
    }

    public record Decision(boolean allowed, String commandRoot, String reason) {
    }
}
