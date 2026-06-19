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
