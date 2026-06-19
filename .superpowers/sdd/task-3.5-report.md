## Fix pass: 26.1 messaging API regression

### Files changed
- `/workspaces/LumiChat/src/main/java/com/riceawa/llm/compat/MessageCompat.java`
- `/workspaces/LumiChat/src/main/java/com/riceawa/llm/template/TemplateEditor.java`
- `/workspaces/LumiChat/src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java`
- `/workspaces/LumiChat/src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java`
- `/workspaces/LumiChat/src/main/java/com/riceawa/llm/command/LLMChatCommand.java`
- `/workspaces/LumiChat/src/main/java/com/riceawa/llm/context/ChatContextManager.java`

### Root cause and fix
- 26.1 Mojang mappings no longer expose `Player.displayClientMessage(Component, boolean)` / `ServerPlayer.displayClientMessage(Component, boolean)`.
- `javap` on `/home/codespace/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common-deobf/26.1/minecraft-common-deobf-26.1.jar` showed 26.1 provides `sendSystemMessage(Component)` and `sendOverlayMessage(Component)` on `Player`.
- Added `MessageCompat.displayClientMessage(Player, Component, boolean)` with Stonecutter conditionals:
  - `>=26.1`: route overlay messages to `sendOverlayMessage`, normal messages to `sendSystemMessage`.
  - older sampled versions: keep `displayClientMessage`.
- Replaced scoped direct messaging calls with the helper in messaging/template/chat-context/command paths that 26.1 compile reported.

### Commands and results
- `JAVA_HOME=/usr/local/sdkman/candidates/java/25.0.2-ms ./gradlew :26.1:compileJava --console=plain` — reproduced 26.1 `displayClientMessage` missing compile errors.
- `JAVA_HOME=/usr/local/sdkman/candidates/java/25.0.2-ms ./gradlew :26.1:build --console=plain` — passed after fix (`BUILD SUCCESSFUL`).
- `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.19:build :1.20.6:build :1.21.11:build --console=plain` — passed after fix (`BUILD SUCCESSFUL`).

### Notes
- Scope limited to messaging compatibility. No Identifier/ResourceLocation, GameRules, permission, EntityHelper, player lookup, teleport-coordinate, model config, or Gradle changes were made.
