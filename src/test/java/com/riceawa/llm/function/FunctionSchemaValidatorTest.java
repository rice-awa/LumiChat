package com.riceawa.llm.function;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FunctionSchemaValidatorTest {

    @Test
    void passesWhenArgumentsMatchSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        properties.add("name", nameProp);
        schema.add("properties", properties);

        JsonObject args = new JsonObject();
        args.addProperty("name", "test");

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertTrue(result.isValid());
        assertNull(result.error());
    }

    @Test
    void rejectsUnknownFieldWhenAdditionalPropertiesFalse() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        JsonObject properties = new JsonObject();
        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        properties.add("name", nameProp);
        schema.add("properties", properties);

        JsonObject args = new JsonObject();
        args.addProperty("name", "test");
        args.addProperty("unknown_field", "value");

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertFalse(result.isValid());
        assertTrue(result.error().contains("未知参数"));
        assertTrue(result.error().contains("unknown_field"));
    }

    @Test
    void rejectsMissingRequiredField() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        properties.add("name", nameProp);
        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("name");
        schema.add("required", required);

        JsonObject args = new JsonObject();

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertFalse(result.isValid());
        assertTrue(result.error().contains("缺少必需参数"));
        assertTrue(result.error().contains("name"));
    }

    @Test
    void rejectsIntegerWithFractionalValue() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject countProp = new JsonObject();
        countProp.addProperty("type", "integer");
        countProp.addProperty("minimum", 0);
        properties.add("count", countProp);
        schema.add("properties", properties);

        JsonObject args = new JsonObject();
        args.addProperty("count", 3.14);

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertFalse(result.isValid());
        assertTrue(result.error().contains("count"));
        assertTrue(result.error().contains("整数"));
    }

    @Test
    void passesIntegerWithWholeNumberValue() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject countProp = new JsonObject();
        countProp.addProperty("type", "integer");
        properties.add("count", countProp);
        schema.add("properties", properties);

        JsonObject args = new JsonObject();
        args.addProperty("count", 42);

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertTrue(result.isValid());
    }

    @Test
    void rejectsWrongType() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject countProp = new JsonObject();
        countProp.addProperty("type", "integer");
        properties.add("count", countProp);
        schema.add("properties", properties);

        JsonObject args = new JsonObject();
        args.addProperty("count", "not_a_number");

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertFalse(result.isValid());
        assertTrue(result.error().contains("count"));
        assertTrue(result.error().contains("integer"));
    }

    @Test
    void rejectsEnumValueNotInList() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject msgType = new JsonObject();
        msgType.addProperty("type", "string");
        JsonArray enumValues = new JsonArray();
        enumValues.add("chat");
        enumValues.add("system");
        msgType.add("enum", enumValues);
        properties.add("message_type", msgType);
        schema.add("properties", properties);

        JsonObject args = new JsonObject();
        args.addProperty("message_type", "invalid");

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertFalse(result.isValid());
        assertTrue(result.error().contains("message_type"));
        assertTrue(result.error().contains("枚举"));
    }

    @Test
    void rejectsValueBelowMinimum() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject radius = new JsonObject();
        radius.addProperty("type", "number");
        radius.addProperty("minimum", 1);
        radius.addProperty("maximum", 64);
        properties.add("radius", radius);
        schema.add("properties", properties);

        JsonObject args = new JsonObject();
        args.addProperty("radius", 0);

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertFalse(result.isValid());
        assertTrue(result.error().contains("radius"));
        assertTrue(result.error().contains("最小值"));
    }

    @Test
    void rejectsValueAboveMaximum() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject radius = new JsonObject();
        radius.addProperty("type", "number");
        radius.addProperty("minimum", 1);
        radius.addProperty("maximum", 64);
        properties.add("radius", radius);
        schema.add("properties", properties);

        JsonObject args = new JsonObject();
        args.addProperty("radius", 100);

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertFalse(result.isValid());
        assertTrue(result.error().contains("radius"));
        assertTrue(result.error().contains("最大值"));
    }

    @Test
    void rejectsStringBelowMinLength() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "string");
        msg.addProperty("minLength", 1);
        properties.add("message", msg);
        schema.add("properties", properties);

        JsonObject args = new JsonObject();
        args.addProperty("message", "");

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertFalse(result.isValid());
        assertTrue(result.error().contains("message"));
        assertTrue(result.error().contains("至少"));
    }

    @Test
    void rejectsStringAboveMaxLength() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "string");
        msg.addProperty("maxLength", 16);
        properties.add("name", msg);
        schema.add("properties", properties);

        JsonObject args = new JsonObject();
        args.addProperty("name", "a".repeat(32));

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertFalse(result.isValid());
        assertTrue(result.error().contains("name"));
        assertTrue(result.error().contains("不能超过"));
    }

    @Test
    void rejectsTeleportOneOfWhenNeitherSatisfied() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject playerName = new JsonObject();
        playerName.addProperty("type", "string");
        properties.add("player_name", playerName);
        JsonObject targetPlayer = new JsonObject();
        targetPlayer.addProperty("type", "string");
        properties.add("target_player", targetPlayer);
        JsonObject xProp = new JsonObject();
        xProp.addProperty("type", "number");
        properties.add("x", xProp);
        JsonObject yProp = new JsonObject();
        yProp.addProperty("type", "number");
        properties.add("y", yProp);
        JsonObject zProp = new JsonObject();
        zProp.addProperty("type", "number");
        properties.add("z", zProp);
        schema.add("properties", properties);

        JsonArray oneOf = new JsonArray();
        JsonObject opt1 = new JsonObject();
        JsonArray req1 = new JsonArray();
        req1.add("target_player");
        opt1.add("required", req1);
        oneOf.add(opt1);
        JsonObject opt2 = new JsonObject();
        JsonArray req2 = new JsonArray();
        req2.add("x");
        req2.add("y");
        req2.add("z");
        opt2.add("required", req2);
        oneOf.add(opt2);
        schema.add("oneOf", oneOf);

        JsonObject args = new JsonObject();
        args.addProperty("player_name", "Steve");

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertFalse(result.isValid());
        assertTrue(result.error().contains("oneOf"));
    }

    @Test
    void acceptsTeleportOneOfWhenTargetPlayerProvided() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject targetPlayer = new JsonObject();
        targetPlayer.addProperty("type", "string");
        properties.add("target_player", targetPlayer);
        schema.add("properties", properties);

        JsonArray oneOf = new JsonArray();
        JsonObject opt1 = new JsonObject();
        JsonArray req1 = new JsonArray();
        req1.add("target_player");
        opt1.add("required", req1);
        oneOf.add(opt1);
        JsonObject opt2 = new JsonObject();
        JsonArray req2 = new JsonArray();
        req2.add("x");
        req2.add("y");
        req2.add("z");
        opt2.add("required", req2);
        oneOf.add(opt2);
        schema.add("oneOf", oneOf);

        JsonObject args = new JsonObject();
        args.addProperty("target_player", "Alex");

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertTrue(result.isValid());
    }

    @Test
    void acceptsTeleportOneOfWhenCoordinatesProvided() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject xProp = new JsonObject();
        xProp.addProperty("type", "number");
        properties.add("x", xProp);
        JsonObject yProp = new JsonObject();
        yProp.addProperty("type", "number");
        properties.add("y", yProp);
        JsonObject zProp = new JsonObject();
        zProp.addProperty("type", "number");
        properties.add("z", zProp);
        schema.add("properties", properties);

        JsonArray oneOf = new JsonArray();
        JsonObject opt1 = new JsonObject();
        JsonArray req1 = new JsonArray();
        req1.add("target_player");
        opt1.add("required", req1);
        oneOf.add(opt1);
        JsonObject opt2 = new JsonObject();
        JsonArray req2 = new JsonArray();
        req2.add("x");
        req2.add("y");
        req2.add("z");
        opt2.add("required", req2);
        oneOf.add(opt2);
        schema.add("oneOf", oneOf);

        JsonObject args = new JsonObject();
        args.addProperty("x", 100);
        args.addProperty("y", 64);
        args.addProperty("z", 200);

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertTrue(result.isValid());
    }

    @Test
    void rejectsTeleportOneOfWhenBothAlternativesMatch() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject targetPlayer = new JsonObject();
        targetPlayer.addProperty("type", "string");
        properties.add("target_player", targetPlayer);
        JsonObject xProp = new JsonObject();
        xProp.addProperty("type", "number");
        properties.add("x", xProp);
        JsonObject yProp = new JsonObject();
        yProp.addProperty("type", "number");
        properties.add("y", yProp);
        JsonObject zProp = new JsonObject();
        zProp.addProperty("type", "number");
        properties.add("z", zProp);
        schema.add("properties", properties);

        JsonArray oneOf = new JsonArray();
        JsonObject opt1 = new JsonObject();
        JsonArray req1 = new JsonArray();
        req1.add("target_player");
        opt1.add("required", req1);
        oneOf.add(opt1);
        JsonObject opt2 = new JsonObject();
        JsonArray req2 = new JsonArray();
        req2.add("x");
        req2.add("y");
        req2.add("z");
        opt2.add("required", req2);
        oneOf.add(opt2);
        schema.add("oneOf", oneOf);

        JsonObject args = new JsonObject();
        args.addProperty("target_player", "Alex");
        args.addProperty("x", 100);
        args.addProperty("y", 64);
        args.addProperty("z", 200);

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertFalse(result.isValid());
        assertTrue(result.error().contains("恰好一组"));
    }

    @Test
    void errorMessageDoesNotEchoSensitiveValues() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject msgType = new JsonObject();
        msgType.addProperty("type", "string");
        JsonArray enumValues = new JsonArray();
        enumValues.add("chat");
        msgType.add("enum", enumValues);
        properties.add("message_type", msgType);
        schema.add("properties", properties);

        JsonObject args = new JsonObject();
        args.addProperty("message_type", "malicious_value_here");

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertFalse(result.isValid());
        assertFalse(result.error().contains("malicious_value_here"));
    }

    @Test
    void passesWithEmptyArgumentsForNoPropertySchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());

        JsonObject args = new JsonObject();

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(args, schema);
        assertTrue(result.isValid());
    }

    @Test
    void handlesNullArgumentsGracefully() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());

        FunctionSchemaValidator.ValidationResult result = FunctionSchemaValidator.validate(null, schema);
        assertTrue(result.isValid());
    }
}
