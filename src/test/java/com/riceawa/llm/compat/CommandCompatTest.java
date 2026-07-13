//? >=1.21.11 {
package com.riceawa.llm.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandCompatTest {

    @Test
    void reportsTheCallbackResultOnlyWhenCommandExecutionSucceeded() {
        assertEquals(5, CommandCompat.resultCodeForCallback(true, true, 5));
        assertEquals(0, CommandCompat.resultCodeForCallback(true, false, 5));
        assertEquals(0, CommandCompat.resultCodeForCallback(false, true, 5));
    }
}
//?}