package com.riceawa.llm.function;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionRegistryAuditPolicyTest {

    @Test
    void reservesExecuteCommandForItsCommandSpecificAuditSchema() {
        assertFalse(FunctionRegistry.shouldAuditGeneric("execute_command"));
        assertTrue(FunctionRegistry.shouldAuditGeneric("get_time"));
    }
}
