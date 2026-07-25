### Task 12: 统一 Tool Call JSON Schema 与运行时参数验证

**Files:**
- Create: `src/main/java/com/riceawa/llm/function/FunctionSchemaValidator.java`
- Modify: `src/main/java/com/riceawa/llm/function/FunctionRegistry.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/*.java`
- Modify: `src/main/java/com/riceawa/llm/function/FunctionRegistry.java` 内嵌基础函数 Schema
- Create: `src/test/java/com/riceawa/llm/function/FunctionSchemaValidatorTest.java`
- Create: `src/test/java/com/riceawa/llm/function/FunctionSchemaContractTest.java`

**Interfaces:**
- Produces: `FunctionSchemaValidator.validate(JsonObject arguments, JsonObject schema) -> ValidationResult`。
- 支持的 Schema 子集：`type`、`required`、`additionalProperties`、`enum`、`minimum`、`maximum`、`minLength`、`maxLength`、`oneOf`。

- [ ] **Step 1: 写 validator 单元测试**

覆盖未知字段、缺必填、错类型、enum、数值范围、字符串范围、teleport 的 `oneOf(target_player | x+y+z)`。错误消息只列参数名和规则，不回显敏感值。

- [ ] **Step 2: 在 FunctionRegistry 执行前验证**

权限检查后、函数 `execute` 前调用 validator；失败返回 `FunctionResult.error("参数验证失败: " + result.error())`。工具定义和运行时使用同一份 `getParametersSchema()`。

- [ ] **Step 3: 全部 Schema fail-closed**

每个 object schema 添加：

```java
schema.addProperty("additionalProperties", false);
```

并落实：`message_type` enum；玩家名/ID/查询/消息 maxLength；数量与坐标 range；`TeleportPlayerFunction` oneOf；无参数函数使用空 properties + false。

- [ ] **Step 4: 写契约测试**

通过 `FunctionRegistry.getInstance().getAllFunctions()` 取得包括内嵌基础函数在内的真实注册集合，断言 name 唯一、schema type 为 object、包含 `additionalProperties:false`、required 字段存在于 properties、enum 默认值属于 enum；不得在测试中维护第二份手写函数清单。

- [ ] **Step 5: 测试与构建**

```bash
./gradlew :1.21.11:test --tests 'com.riceawa.llm.function.FunctionSchema*'
./gradlew :1.19:build :1.21.11:build
```

Expected: PASS / BUILD SUCCESSFUL。

- [ ] **Step 6: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/function/FunctionSchemaValidator.java \
  src/main/java/com/riceawa/llm/function/FunctionRegistry.java \
  src/main/java/com/riceawa/llm/function/impl \
  src/test/java/com/riceawa/llm/function/FunctionSchemaValidatorTest.java \
  src/test/java/com/riceawa/llm/function/FunctionSchemaContractTest.java
git commit -m "fix(functions): 严格校验工具调用参数Schema"
```

---

