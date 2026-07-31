package com.riceawa.llm.function;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandExecutionPolicyTest {

    @Test
    void rejectsWhenDisabledEvenForAnOperator() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                "list", true, false, Set.of());

        assertFalse(decision.allowed());
        assertEquals("disabled", decision.reason());
    }

    @Test
    void rejectsNonOperators() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                "list", false, true, Set.of());

        assertFalse(decision.allowed());
        assertEquals("not_operator", decision.reason());
    }

    @Test
    void allowsAnyCommandWithAnEmptyBlocklist() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                "list", true, true, Set.of());

        assertTrue(decision.allowed());
        assertEquals("list", decision.commandRoot());
        assertEquals("allowed", decision.reason());
    }

    @Test
    void normalizesCaseAndOneLeadingSlashBeforeBlocklistComparison() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                "  /DeoP  ", true, true, Set.of("deop"));

        assertFalse(decision.allowed());
        assertEquals("deop", decision.commandRoot());
        assertEquals("blocked", decision.reason());
    }

    @Test
    void blocksACommandThatIsInTheBlocklist() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                "kick", true, true, Set.of("kick"));

        assertFalse(decision.allowed());
        assertEquals("kick", decision.commandRoot());
        assertEquals("blocked", decision.reason());
    }

    @Test
    void allowsACommandThatIsNotInTheBlocklist() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                "list", true, true, Set.of("kick", "ban"));

        assertTrue(decision.allowed());
        assertEquals("list", decision.commandRoot());
        assertEquals("allowed", decision.reason());
    }

    @Test
    void comparesOnlyTheTopLevelCommandForBlocking() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                "execute as @a run say blocked", true, true, Set.of("execute"));

        assertFalse(decision.allowed());
        assertEquals("execute", decision.commandRoot());
        assertEquals("blocked", decision.reason());
    }

    @Test
    void rejectsCommandsLongerThan256Characters() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                "a".repeat(257), true, true, Set.of());

        assertFalse(decision.allowed());
        assertEquals("too_long", decision.reason());
    }

    @Test
    void rejectsWhitespacePaddedCommandsLongerThanTheConfiguredLimit() {
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                " ".repeat(253) + "list", true, true, Set.of());

        assertFalse(decision.allowed());
        assertEquals("too_long", decision.reason());
    }

    @Test
    void rejectsControlCharactersAndCommandSeparators() {
        assertEquals("invalid_command", CommandExecutionPolicy.evaluate(
                "list\nstop", true, true, Set.of()).reason());
        assertEquals("invalid_command", CommandExecutionPolicy.evaluate(
                "list;stop", true, true, Set.of()).reason());
        assertEquals("invalid_command", CommandExecutionPolicy.evaluate(
                "list\0stop", true, true, Set.of()).reason());
    }
}
