## Fix pass: sampled project registration

- Commands/results:
  - `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew projects --console=plain`: PASS; listed `:1.19`, `:1.20.6`, `:1.21.11`.
  - `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.19:build :1.20.6:build :1.21.11:build --console=plain`: FAIL after project registration, during compilation.
- Files changed:
  - `/workspaces/LumiChat/settings.gradle.kts`: registered `1.19` via `1.19.4` project mapping and sampled `1.20.6`, `1.21.11`; preserved Java 25 `26.1` conditional.
  - `/workspaces/LumiChat/.superpowers/sdd/task-3.3-report.md`: this report section.
- Commits:
  - `a930ec0 fix(build): 注册抽样多版本项目`.
- Next error clusters:
  - `EntityHelper.java`: 1.21.11 clock/weather APIs unresolved (`WorldClocks`, `Registries.WORLD_CLOCK`, `ServerLevel.clockManager()`, `WeatherData`, `ServerLevel.getWeatherData()`).
  - Player messaging APIs unresolved across template/context/command code (`Player.sendSystemMessage(...)`, `ServerPlayer.sendOverlayMessage(...)`).
## Fix pass: messaging API domain

- Files changed:
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/command/LLMChatCommand.java`
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/context/ChatContextManager.java`
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java`
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java`
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/template/TemplateEditor.java`
  - `/workspaces/LumiChat/.superpowers/sdd/task-3.3-report.md`
- Commands/results:
  - `grep -RInE "sendSystemMessage|sendOverlayMessage" "/workspaces/LumiChat/src/main/java"`: found direct messaging calls in scoped files plus `ExecuteCommandFunction.CommandOutputCollector#sendSystemMessage`, which is an implementation method rather than a direct Player/ServerPlayer messaging call.
  - `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.21.11:compileJava --console=plain`: FAIL, but only on out-of-scope `EntityHelper` clock/weather API errors; no messaging API errors were reported.
  - `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.19:compileJava :1.20.6:compileJava :1.21.11:compileJava --console=plain`: FAIL, but remaining failures are out-of-scope Identifier/ResourceLocation, player lookup by UUID/name, teleport rotation, dimension `.identifier()`, and `EntityHelper` clock/weather domains; no direct Player/ServerPlayer messaging API errors were reported.
  - `grep -RInE "sendSystemMessage|sendOverlayMessage" "/workspaces/LumiChat/src/main/java" "/workspaces/LumiChat/versions/1.19/build/generated/stonecutter/main/java" "/workspaces/LumiChat/versions/1.20.6/build/generated/stonecutter/main/java" "/workspaces/LumiChat/versions/1.21.11/build/generated/stonecutter/main/java"`: FAIL only because the generated 1.21.11 directory did not exist; matches were limited to `ExecuteCommandFunction.CommandOutputCollector#sendSystemMessage`.
- Remaining out-of-scope domains:
  - `EntityHelper` 1.21.11 clock/weather API (`WorldClocks`, `Registries.WORLD_CLOCK`, `ServerLevel.clockManager()`, `WeatherData`, `ServerLevel.getWeatherData()`).
  - Legacy Identifier/ResourceLocation branches in 1.19/1.20.6 generated code.
  - Player lookup methods expecting UUID rather than String in 1.20.6 generated functions.
  - Teleport rotation getters (`getYaw`/`getPitch`) and dimension `.identifier()` usages.

## Fix pass: EntityHelper 26.1 branch leakage

- Files changed:
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/util/EntityHelper.java`: kept the `>=26.1` imports and clock/weather implementations inside inactive Stonecutter comments for raw 1.21.11 source, and made the legacy-compatible `getDayTime`, `setDayTime`, and `setWeatherParameters` calls active in raw source.
  - `/workspaces/LumiChat/.superpowers/sdd/task-3.3-report.md`: this report section.
- Commands/results:
  - `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.21.11:compileJava --console=plain`: RED before fix; failed only with `EntityHelper` unresolved 26.1 clock/weather APIs (`WorldClocks`, `Registries.WORLD_CLOCK`, `clockManager()`, `WeatherData`, `getWeatherData()`).
  - `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.21.11:compileJava --console=plain`: PASS after fix.
  - `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.19:compileJava :1.20.6:compileJava :1.21.11:compileJava --console=plain`: first post-fix run exposed Stonecutter marker parse errors from closing inactive branches; corrected markers.
  - `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.19:compileJava :1.20.6:compileJava :1.21.11:compileJava --console=plain`: FAIL, but Stonecutter generation and `:1.21.11:compileJava` passed; remaining errors are out-of-scope generated 1.19/1.20.6 API domains.
- Remaining out-of-scope domains:
  - Legacy Identifier/ResourceLocation branches (`net.minecraft.resources.Identifier`, `IdentifierCompat`).
  - GameRules package/API differences.
  - Dimension and biome key `.identifier()` usages.
  - Player lookup methods expecting UUID rather than String.
  - Legacy world/height and teleport rotation getter differences.

## Fix pass: legacy Identifier branches

- Commit: `0639540 fix(mappings): 修复旧版本 Identifier 分支`.
- Files changed:
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/compat/IdentifierCompat.java`
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/function/impl/SetBlockFunction.java`
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/function/impl/SummonEntityFunction.java`
- Root cause: `<1.21` Stonecutter branches were still generating `net.minecraft.resources.Identifier`; Mojang-mapped sampled outputs for 1.19/1.20.6 require `net.minecraft.resources.ResourceLocation`.
- Fix:
  - Added Stonecutter-gated imports and return/local types so `>=1.21` keeps `Identifier`, while `<1.21` uses `ResourceLocation`.
  - Updated legacy constructors to `new ResourceLocation(...)`.
  - Updated legacy registry lookups in scoped functions from `BuiltInRegistries.*.getValue(...)` to `BuiltInRegistries.*.get(...)`.
  - Kept 1.21.11 behavior intact.
- Verification:
  - `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.21.11:compileJava --console=plain`: PASS in subagent worktree.
  - `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.19:compileJava :1.20.6:compileJava :1.21.11:compileJava --console=plain`: still failed before the parallel legacy world/permission domain was merged, but scoped Identifier missing/type/constructor errors were cleared.

## Fix pass: legacy world, permission, and lookup APIs

- Commit: `d1569ba fix(mappings): 修复旧版本世界权限 API`.
- Files changed:
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/compat/GameRulesCompat.java`
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/compat/PermissionCompat.java`
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/template/PromptTemplate.java`
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/template/TemplateEditor.java`
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java`
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/function/impl/PlayerStatsFunction.java`
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/function/impl/WorldInfoFunction.java`
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/function/impl/PlayerEffectsFunction.java`
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java`
  - `/workspaces/LumiChat/src/main/java/com/riceawa/llm/function/impl/InventoryFunction.java`
- Fix:
  - Updated old-version `GameRulesCompat` branch for legacy package/API names.
  - Updated old-version `PermissionCompat` branch to `CommandSourceStack#hasPermission(int)`.
  - Updated old-version player-name lookups from `getPlayer(String)` to `getPlayerByName(String)`.
  - Updated old-version ResourceKey accessors from `.identifier()` to `.location()`.
  - Updated old-version teleport rotation branch to Mojang `getYRot()/getXRot()`.
  - Updated old-version min-height accessor to `getMinBuildHeight()`.
- Verification:
  - `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.19:compileJava :1.20.6:compileJava :1.21.11:compileJava --console=plain`: still failed before the parallel Identifier domain was merged, but filtered scoped files/patterns had no remaining errors and `:1.21.11:compileJava` passed in that invocation.

## Final sampled build verification

- Command/result:
  - `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.19:build :1.20.6:build :1.21.11:build --console=plain`: PASS (`BUILD SUCCESSFUL in 27s`, 48 actionable tasks executed).
- Commits included in final pass:
  - `d1569ba fix(mappings): 修复旧版本世界权限 API`
  - `0639540 fix(mappings): 修复旧版本 Identifier 分支`
