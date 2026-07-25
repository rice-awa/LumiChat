package com.riceawa.llm.function;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandExecutionPolicyTest {

    @Test
    void rejectsWhenDisabledEvenForAnOperatorAndAllowlistedCommand() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                "list", true, false, Set.of("list"));

        assertFalse(decision.allowed());
        assertEquals("disabled", decision.reason());
    }

    @Test
    void rejectsNonOperators() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                "list", false, true, Set.of("list"));

        assertFalse(decision.allowed());
        assertEquals("not_operator", decision.reason());
    }

    @Test
    void rejectsAnEmptyAllowlist() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                "list", true, true, Set.of());

        assertFalse(decision.allowed());
        assertEquals("allowlist_empty", decision.reason());
    }

    @Test
    void normalizesCaseAndOneLeadingSlashBeforeAllowlistComparison() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                "  /LiSt  ", true, true, Set.of("list"));

        assertTrue(decision.allowed());
        assertEquals("list", decision.commandRoot());
    }

    @Test
    void comparesOnlyTheTopLevelCommand() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                "execute as @a run say unsafe", true, true, Set.of("say"));

        assertFalse(decision.allowed());
        assertEquals("execute", decision.commandRoot());
        assertEquals("not_allowlisted", decision.reason());
    }

    @Test
    void rejectsCommandsLongerThan256Characters() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                "a".repeat(257), true, true, Set.of("a"));

        assertFalse(decision.allowed());
        assertEquals("too_long", decision.reason());
    }

    @Test
    void rejectsWhitespacePaddedCommandsLongerThanTheConfiguredLimit() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                " ".repeat(253) + "list", true, true, Set.of("list"));

        assertFalse(decision.allowed());
        assertEquals("too_long", decision.reason());
    }

    @Test
    void rejectsControlCharactersAndCommandSeparators() {
        assertEquals("invalid_command", CommandExecutionPolicy.evaluate(
                "list\nstop", true, true, Set.of("list")).reason());
        assertEquals("invalid_command", CommandExecutionPolicy.evaluate(
                "list;stop", true, true, Set.of("list")).reason());
        assertEquals("invalid_command", CommandExecutionPolicy.evaluate(
                "list\0stop", true, true, Set.of("list")).reason());
    }

    @Test
    void allowsAnAllowlistedListCommand() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                "list", true, true, Set.of("list"));

        assertTrue(decision.allowed());
        assertEquals("list", decision.commandRoot());
        assertEquals("allowed", decision.reason());
    }
}
