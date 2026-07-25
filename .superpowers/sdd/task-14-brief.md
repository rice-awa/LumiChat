### Task 14: 抽象注册表、实体创建与传送签名

**Files:**
- Create: `src/main/java/com/riceawa/llm/compat/RegistryCompat.java`
- Create: `src/main/java/com/riceawa/llm/compat/EntityCompat.java`
- Create: `src/main/java/com/riceawa/llm/compat/TeleportCompat.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/SetBlockFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/SummonEntityFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java`

**Interfaces:**
- Produces: `RegistryCompat.getBlock(String) -> Block|null`、`getEntityType(String) -> EntityType<?>|null`。
- Produces: `EntityCompat.create(EntityType<?>, ServerLevel) -> Entity|null`。
- Produces: `TeleportCompat.teleport(ServerPlayer, ServerLevel, double, double, double, float, float) -> void`。

- [ ] **Step 1: 查阅 notable changes 与实际映射**

确认 1.21.2 registry `getValue`、EntitySpawnReason 和 teleport 签名，确认 1.21.11 Identifier rename，确认 26.2 BlockIds 变化是否影响 BuiltInRegistries lookup。结果写 `.superpowers/sdd/compat-registry-teleport.md`。

- [ ] **Step 2: 在 compat 实现条件分支**

RegistryCompat 内部调用 IdentifierCompat；业务调用只传字符串。EntityCompat 封装 `type.create(level, EntitySpawnReason.COMMAND)` 与旧签名。TeleportCompat 封装 1.21.2+ movement flags 参数与旧签名。

- [ ] **Step 3: 迁移三个业务函数**

删除它们的 Identifier/ResourceLocation/BuiltInRegistries/EntitySpawnReason 条件 import 和调用块；错误文案、距离/数量/权限规则保持 Task 7/8/12 的结果。

- [ ] **Step 4: 静态和构建验证**

```bash
grep -R -n '//?' src/main/java/com/riceawa/llm/function/impl/SetBlockFunction.java src/main/java/com/riceawa/llm/function/impl/SummonEntityFunction.java src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java
./gradlew :1.19:build :1.20.6:build :1.21.11:build :26.2:build
```

Expected: grep 无结果；BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/compat/RegistryCompat.java \
  src/main/java/com/riceawa/llm/compat/EntityCompat.java \
  src/main/java/com/riceawa/llm/compat/TeleportCompat.java \
  src/main/java/com/riceawa/llm/function/impl/SetBlockFunction.java \
  src/main/java/com/riceawa/llm/function/impl/SummonEntityFunction.java \
  src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java
git commit -m "refactor(compat): 收敛注册表与传送版本差异"
```

---

