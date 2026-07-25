### Task 13: 抽象玩家查找与维度 ID

**Files:**
- Create: `src/main/java/com/riceawa/llm/compat/PlayerCompat.java`
- Create: `src/main/java/com/riceawa/llm/compat/DimensionCompat.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/InventoryFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/PlayerStatsFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java`
- Modify: `src/main/java/com/riceawa/llm/function/impl/WorldInfoFunction.java`
- Modify: `src/main/java/com/riceawa/llm/template/PromptTemplate.java`
- Modify: `src/main/java/com/riceawa/llm/template/TemplateEditor.java`

**Interfaces:**
- Produces: `PlayerCompat.getPlayerByName(MinecraftServer, String) -> ServerPlayer|null`。
- Produces: `PlayerCompat.isOnGround(ServerPlayer) -> boolean`。
- Produces: `DimensionCompat.getDimensionId(Level) -> String`、`getDisplayName(Level) -> String`。

- [ ] **Step 1: 先查版本 API**

只读侦察报告必须确认 1.19、1.21.11、26.2 的 player lookup、dimension key 和 on-ground API，并写入 `.superpowers/sdd/compat-player-dimension.md`。

- [ ] **Step 2: compat 内实现条件分支**

`getPlayerByName` 在 1.21.11+ 使用 `getPlayer(name)`，旧版本使用 `getPlayerByName(name)`；dimension 在 1.21.11+ 使用 `identifier()`，旧版本使用 `location()`；onGround 旧分支使用实际映射名。

- [ ] **Step 3: 迁移全部调用点**

列出文件中的玩家查找、维度 ID、dimension display switch、onGround 条件块，全部替换为 compat 调用。业务文件不导入 `Identifier/ResourceLocation`。

- [ ] **Step 4: 静态和多版本验证**

```bash
grep -R -n '//?' src/main/java/com/riceawa/llm/function/impl/InventoryFunction.java src/main/java/com/riceawa/llm/function/impl/PlayerStatsFunction.java src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java src/main/java/com/riceawa/llm/function/impl/WorldInfoFunction.java src/main/java/com/riceawa/llm/template/PromptTemplate.java src/main/java/com/riceawa/llm/template/TemplateEditor.java
./gradlew :1.19:build :1.21.11:build :26.2:build
```

Expected: grep 无结果；Java 25 环境中三个版本 BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add \
  src/main/java/com/riceawa/llm/compat/PlayerCompat.java \
  src/main/java/com/riceawa/llm/compat/DimensionCompat.java \
  src/main/java/com/riceawa/llm/function/impl/InventoryFunction.java \
  src/main/java/com/riceawa/llm/function/impl/PlayerStatsFunction.java \
  src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java \
  src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java \
  src/main/java/com/riceawa/llm/function/impl/WorldInfoFunction.java \
  src/main/java/com/riceawa/llm/template/PromptTemplate.java \
  src/main/java/com/riceawa/llm/template/TemplateEditor.java
git commit -m "refactor(compat): 收敛玩家与维度版本差异"
```

---

