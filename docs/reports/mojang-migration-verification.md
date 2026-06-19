# Mojang mappings migration verification

Date: 2026-06-19

## Build verification

- Java 21 `:1.21.11:build`: passed earlier in Phase 3.1; artifact `versions/1.21.11/build/libs/lumichat-2.0.1+1.21.11.jar` produced.
- Java 25 `:26.1:build`: passed after 26.1 messaging compatibility fix.
- Java 21 sampled build: `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.19:build :1.20.6:build :1.21.11:build --console=plain` passed.
- Java 21 `buildAndCollect`: `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew buildAndCollect --console=plain` passed.

Collected artifacts in `build/libs/2.0.1/`:

- `lumichat-2.0.1+1.19.4.jar`
- `lumichat-2.0.1+1.19.4-sources.jar`
- `lumichat-2.0.1+1.20.6.jar`
- `lumichat-2.0.1+1.20.6-sources.jar`
- `lumichat-2.0.1+1.21.11.jar`
- `lumichat-2.0.1+1.21.11-sources.jar`

## 1.21.11 server smoke

Command:

```bash
timeout 120s env JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.21.11:runServer --console=plain
```

Result:

- Server launch reached mod initialization before environment EULA stop.
- Fabric loaded `lumichat 2.0.1` on Minecraft `1.21.11` with Java 21.
- Mixin subsystem initialized; no Mixin injection failure appeared before the EULA stop.
- LumiChat initialization completed:
  - configuration manager initialized,
  - log manager initialized,
  - prompt template manager initialized,
  - function registry initialized,
  - LLM service manager initialized,
  - chat context manager initialized,
  - `LLM Chat Mod initialized successfully!`.
- The run stopped at the expected local environment EULA gate: `You need to agree to the EULA in order to run the server.`

Manual command/function execution was not possible in this non-interactive EULA-blocked 1.21.11 run.

## 26.1 server smoke

Command:

```bash
timeout 120s env JAVA_HOME=/usr/local/sdkman/candidates/java/25.0.2-ms ./gradlew :26.1:runServer --console=plain
```

Result:

- Fabric loaded `lumichat 2.0.1` on Minecraft `26.1` with Java 25.
- Mixin subsystem initialized; no Mixin injection failure appeared.
- LumiChat initialization completed:
  - configuration manager initialized,
  - log manager initialized,
  - prompt template manager initialized,
  - function registry initialized,
  - LLM service manager initialized,
  - chat context manager initialized,
  - `LLM Chat Mod initialized successfully!`.
- Commands registered: `LLM Chat commands registered`.
- Dedicated server reached ready state: `Done (6.358s)! For help, type "help"`.
- The run later idled normally: `Server empty for 60 seconds, pausing`.

No live player was attached in this environment, so `/llmchat` command execution and LLM function invocation were not performed interactively.
