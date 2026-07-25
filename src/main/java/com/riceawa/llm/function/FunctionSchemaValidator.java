package com.riceawa.llm.function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

public final class FunctionSchemaValidator {

    private FunctionSchemaValidator() {
    }

    public static ValidationResult validate(JsonObject arguments, JsonObject schema) {
        JsonObject safeArguments = arguments == null ? new JsonObject() : arguments;

        if (!schema.has("type") || !"object".equals(schema.get("type").getAsString())) {
            return ValidationResult.failure("schema必须定义type为object");
        }

        String unknownError = checkAdditionalProperties(safeArguments, schema);
        if (unknownError != null) {
            return ValidationResult.failure(unknownError);
        }

        String requiredError = checkRequired(safeArguments, schema);
        if (requiredError != null) {
            return ValidationResult.failure(requiredError);
        }

        if (schema.has("properties")) {
            JsonObject properties = schema.getAsJsonObject("properties");
            String propError = checkProperties(safeArguments, properties);
            if (propError != null) {
                return ValidationResult.failure(propError);
            }
        }

        if (schema.has("oneOf")) {
            JsonArray oneOf = schema.getAsJsonArray("oneOf");
            String oneOfError = checkOneOf(safeArguments, oneOf);
            if (oneOfError != null) {
                return ValidationResult.failure(oneOfError);
            }
        }

        return ValidationResult.success();
    }

    private static String checkAdditionalProperties(JsonObject arguments, JsonObject schema) {
        if (!schema.has("additionalProperties")) {
            return null;
        }
        boolean allowAdditional = schema.get("additionalProperties").getAsBoolean();
        if (allowAdditional) {
            return null;
        }

        JsonObject properties = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
        for (String key : arguments.keySet()) {
            if (!properties.has(key)) {
                return "未知参数: " + key;
            }
        }
        return null;
    }

    private static String checkRequired(JsonObject arguments, JsonObject schema) {
        if (!schema.has("required")) {
            return null;
        }
        JsonArray required = schema.getAsJsonArray("required");
        List<String> missing = new ArrayList<>();
        for (JsonElement element : required) {
            String field = element.getAsString();
            if (!arguments.has(field)) {
                missing.add(field);
            }
        }
        if (!missing.isEmpty()) {
            return "缺少必需参数: " + String.join(", ", missing);
        }
        return null;
    }

    private static String checkProperties(JsonObject arguments, JsonObject properties) {
        for (String name : properties.keySet()) {
            if (!arguments.has(name)) {
                continue;
            }
            JsonObject propSchema = properties.getAsJsonObject(name);
            JsonElement value = arguments.get(name);

            if (propSchema.has("enum")) {
                String enumError = checkEnum(name, value, propSchema.getAsJsonArray("enum"));
                if (enumError != null) {
                    return enumError;
                }
            }

            String typeError = checkType(name, value, propSchema);
            if (typeError != null) {
                return typeError;
            }

            if (value.isJsonPrimitive()) {
                JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (primitive.isNumber() && propSchema.has("type")
                        && ("number".equals(propSchema.get("type").getAsString())
                        || "integer".equals(propSchema.get("type").getAsString()))) {
                    String rangeError = checkNumericConstraints(name, primitive, propSchema);
                    if (rangeError != null) {
                        return rangeError;
                    }
                }
                if (primitive.isString()) {
                    String stringError = checkStringConstraints(name, primitive, propSchema);
                    if (stringError != null) {
                        return stringError;
                    }
                }
            }
        }
        return null;
    }

    private static String checkType(String name, JsonElement value, JsonObject propSchema) {
        if (!propSchema.has("type")) {
            return null;
        }
        String expected = propSchema.get("type").getAsString();
        switch (expected) {
            case "string":
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    return "参数 '" + name + "' 的类型必须为string";
                }
                break;
            case "number":
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    return "参数 '" + name + "' 的类型必须为number";
                }
                break;
            case "integer":
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    return "参数 '" + name + "' 的类型必须为integer";
                }
                break;
            case "boolean":
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                    return "参数 '" + name + "' 的类型必须为boolean";
                }
                break;
            case "array":
                if (!value.isJsonArray()) {
                    return "参数 '" + name + "' 的类型必须为array";
                }
                break;
            default:
                break;
        }
        return null;
    }

    private static String checkEnum(String name, JsonElement value, JsonArray enumValues) {
        String valueStr;
        if (value.isJsonPrimitive()) {
            valueStr = value.getAsString();
        } else {
            return "参数 '" + name + "' 的值不在允许的枚举值中";
        }
        for (JsonElement element : enumValues) {
            if (element.getAsString().equals(valueStr)) {
                return null;
            }
        }
        return "参数 '" + name + "' 的值不在允许的枚举值中";
    }

    private static String checkNumericConstraints(String name, JsonPrimitive value, JsonObject propSchema) {
        double num = value.getAsDouble();
        if (propSchema.has("minimum") && num < propSchema.get("minimum").getAsDouble()) {
            return "参数 '" + name + "' 的值小于最小值 " + formatNum(propSchema.get("minimum").getAsDouble());
        }
        if (propSchema.has("maximum") && num > propSchema.get("maximum").getAsDouble()) {
            return "参数 '" + name + "' 的值大于最大值 " + formatNum(propSchema.get("maximum").getAsDouble());
        }
        return null;
    }

    private static String checkStringConstraints(String name, JsonPrimitive value, JsonObject propSchema) {
        String str = value.getAsString();
        int len = str.length();
        if (propSchema.has("minLength") && len < propSchema.get("minLength").getAsInt()) {
            return "参数 '" + name + "' 的长度必须至少 " + propSchema.get("minLength").getAsInt();
        }
        if (propSchema.has("maxLength") && len > propSchema.get("maxLength").getAsInt()) {
            return "参数 '" + name + "' 的长度不能超过 " + propSchema.get("maxLength").getAsInt();
        }
        return null;
    }

    private static String checkOneOf(JsonObject arguments, JsonArray oneOf) {
        for (JsonElement element : oneOf) {
            JsonObject option = element.getAsJsonObject();
            JsonArray optionRequired = option.getAsJsonArray("required");
            if (optionRequired == null) {
                continue;
            }
            boolean allPresent = true;
            for (JsonElement req : optionRequired) {
                if (!arguments.has(req.getAsString())) {
                    allPresent = false;
                    break;
                }
            }
            if (allPresent) {
                return null;
            }
        }

        List<String> alternatives = new ArrayList<>();
        for (JsonElement element : oneOf) {
            JsonObject option = element.getAsJsonObject();
            JsonArray optionRequired = option.getAsJsonArray("required");
            if (optionRequired == null) {
                continue;
            }
            List<String> fields = new ArrayList<>();
            for (JsonElement req : optionRequired) {
                fields.add(req.getAsString());
            }
            alternatives.add("{" + String.join(", ", fields) + "}");
        }
        return "参数不符合oneOf约束，需满足以下之一: " + String.join(" 或 ", alternatives);
    }

    private static String formatNum(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    public record ValidationResult(boolean isValid, String error) {
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String error) {
            return new ValidationResult(false, error);
        }
    }
}
