package com.riceawa.llm.function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FunctionSchemaContractTest {

    @Test
    void allFunctionNamesAreUnique() {
        Collection<LLMFunction> functions = FunctionRegistry.getInstance().getAllFunctions();
        Set<String> names = new HashSet<>();
        for (LLMFunction function : functions) {
            String name = function.getName();
            assertFalse(names.contains(name), "Duplicate function name: " + name);
            names.add(name);
        }
    }

    @Test
    void everySchemaHasTypeObject() {
        for (LLMFunction function : FunctionRegistry.getInstance().getAllFunctions()) {
            JsonObject schema = function.getParametersSchema();
            assertNotNull(schema, function.getName() + " schema is null");
            assertTrue(schema.has("type"), function.getName() + " schema missing type");
            assertEquals("object", schema.get("type").getAsString(),
                    function.getName() + " schema type is not 'object'");
        }
    }

    @Test
    void everySchemaHasAdditionalPropertiesFalse() {
        for (LLMFunction function : FunctionRegistry.getInstance().getAllFunctions()) {
            JsonObject schema = function.getParametersSchema();
            assertTrue(schema.has("additionalProperties"),
                    function.getName() + " schema missing additionalProperties");
            assertFalse(schema.get("additionalProperties").getAsBoolean(),
                    function.getName() + " schema additionalProperties is not false");
        }
    }

    @Test
    void requiredFieldsExistInProperties() {
        for (LLMFunction function : FunctionRegistry.getInstance().getAllFunctions()) {
            JsonObject schema = function.getParametersSchema();
            if (!schema.has("required")) {
                continue;
            }
            JsonArray required = schema.getAsJsonArray("required");
            assertTrue(schema.has("properties"),
                    function.getName() + " has required but no properties");
            JsonObject properties = schema.getAsJsonObject("properties");
            for (JsonElement element : required) {
                String field = element.getAsString();
                assertTrue(properties.has(field),
                        function.getName() + " required field '" + field + "' not in properties");
            }
        }
    }

    @Test
    void enumDefaultsBelongToEnumValues() {
        for (LLMFunction function : FunctionRegistry.getInstance().getAllFunctions()) {
            JsonObject schema = function.getParametersSchema();
            if (!schema.has("properties")) {
                continue;
            }
            JsonObject properties = schema.getAsJsonObject("properties");
            for (String key : properties.keySet()) {
                JsonObject prop = properties.getAsJsonObject(key);
                if (!prop.has("enum") || !prop.has("default")) {
                    continue;
                }
                JsonArray enumValues = prop.getAsJsonArray("enum");
                String defaultValue = prop.get("default").getAsString();
                boolean found = false;
                for (JsonElement enumValue : enumValues) {
                    if (enumValue.getAsString().equals(defaultValue)) {
                        found = true;
                        break;
                    }
                }
                assertTrue(found,
                        function.getName() + " default '" + defaultValue
                                + "' for '" + key + "' not in enum values");
            }
        }
    }

    @Test
    void oneOfOptionsReferToExistingProperties() {
        for (LLMFunction function : FunctionRegistry.getInstance().getAllFunctions()) {
            JsonObject schema = function.getParametersSchema();
            if (!schema.has("oneOf") || !schema.has("properties")) {
                continue;
            }
            JsonArray oneOf = schema.getAsJsonArray("oneOf");
            JsonObject properties = schema.getAsJsonObject("properties");
            for (JsonElement element : oneOf) {
                JsonObject option = element.getAsJsonObject();
                assertTrue(option.has("required"),
                        function.getName() + " oneOf option missing required");
                JsonArray required = option.getAsJsonArray("required");
                for (JsonElement req : required) {
                    String field = req.getAsString();
                    assertTrue(properties.has(field),
                            function.getName() + " oneOf field '" + field + "' not in properties");
                }
            }
        }
    }
}
