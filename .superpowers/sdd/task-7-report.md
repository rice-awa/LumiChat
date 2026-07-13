# Task 7 implementation report: execute_command explicit allowlist

## Files changed

- `src/main/java/com/riceawa/llm/function/CommandExecutionPolicy.java` (new)
- `src/main/java/com/riceawa/llm/function/PermissionHelper.java`
- `src/main/java/com/riceawa/llm/function/impl/ExecuteCommandFunction.java`
- `src/main/java/com/riceawa/llm/config/ConfigDefaults.java`
- `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`
- `src/test/java/com/riceawa/llm/function/CommandExecutionPolicyTest.java` (new)

`progress.md` was already modified by Task 6 when this task began and was intentionally left untouched.

## TDD evidence

### RED

Tests were created before production code. The focused Gradle command initially could not start because the environment defaulted to Java 11. With the available Java 21 compiler, this RED command was run:

```bash
/usr/local/sdkman/candidates/java/21.0.10-ms/bin/javac \
  -cp /home/codespace/.gradle/caches/modules-2/files-2.1/org.junit.jupiter/junit-jupiter-api/5.10.2/fb55d6e2bce173f35fd28422e7975539621055ef/junit-jupiter-api-5.10.2.jar \
  -d /tmp/lumichat-task7-red \
  src/test/java/com/riceawa/llm/function/CommandExecutionPolicyTest.java
```

Result: exit code `1`, with the expected RED errors that `CommandExecutionPolicy` did not exist (`package CommandExecutionPolicy does not exist` / `cannot find symbol`).

### GREEN

Temporary Eclipse Temurin 17 and 21 toolchains were installed outside the repository because Gradle requires vendor-matching toolchains and none were initially available. The required focused test was then run against the remediation worktree:

```bash
JAVA_HOME=/tmp/gradle-init/temurin-21 \
PATH=/tmp/gradle-init/temurin-21/bin:$PATH \
./gradlew --project-dir /workspaces/LumiChat/.worktrees/multiversion-remediation \
  -Dorg.gradle.java.installations.paths=/tmp/gradle-init/temurin-17,/tmp/gradle-init/temurin-21 \
  :1.21.11:test --tests com.riceawa.llm.function.CommandExecutionPolicyTest
```

Result: `BUILD SUCCESSFUL`; XML result: `tests="8" skipped="0" failures="0" errors="0"`.

Final validation command:

```bash
JAVA_HOME=/tmp/gradle-init/temurin-21 \
PATH=/tmp/gradle-init/temurin-21/bin:$PATH \
./gradlew --project-dir /workspaces/LumiChat/.worktrees/multiversion-remediation \
  -Dorg.gradle.java.installations.paths=/tmp/gradle-init/temurin-17,/tmp/gradle-init/temurin-21 \
  :1.21.11:test --tests com.riceawa.llm.function.CommandExecutionPolicyTest \
  :1.19:build :1.21.11:build
```

Result: `BUILD SUCCESSFUL` in 19s; all 30 requested task actions completed successfully (8 executed, 22 up-to-date). The 1.19 compilation confirms the shared production source remains Java 17 compatible.

## Policy, configuration, and security decisions

- Added immutable `CommandExecutionPolicy.Decision(boolean allowed, String commandRoot, String reason)` and a fail-closed evaluator.
- The evaluator rejects disabled execution, non-OP callers, empty allowlists, malformed input, commands longer than 256 characters, and roots absent from the allowlist.
- It accepts only one normalized command input: rejects newline, carriage return, NUL, and semicolon; trims surrounding whitespace; strips one leading slash; compares only the top-level root in `Locale.ROOT` lowercase.
- Focused tests cover every required case: disabled, non-OP, empty allowlist, case/slash normalization, nested `execute ... run say` denial, 257-character denial, and allowlisted `list`; separator/control-character rejection was added as direct coverage of the single-input rule.
- Added configuration fields with strict migration defaults for absent legacy fields: `enableExecuteCommand=false`, `executeCommandAllowlist=[]`, and `executeCommandMaxLength=256`. Fields are included in load/save, validated, and exposed via defensive-copy getters/setters. No legacy blacklist is retained as a fallback.
- Removed the command blacklist APIs from `PermissionHelper`; no remaining production callers use them.
- `ExecuteCommandFunction.isEnabled()` now follows the configuration toggle and `hasPermission()` still requires an OP.
- Commands require a `ServerPlayer` and execute only via that initiating player’s source:
  `serverPlayer.createCommandSourceStack().withSource(outputCapture)`, followed by `CommandCompat.executeCommand(server, source, command)`. No console source or direct dispatcher fallback remains in the function.
- Removed all `System.out`/`System.err` usage and raw-command debug output from `ExecuteCommandFunction`.

## Audit-field evidence

`ExecuteCommandFunction.audit` emits exactly these metadata fields to the audit category:

1. `actor_uuid`
2. `command_root`
3. `command_sha256`
4. `result_code`
5. `duration_ms`
6. `success`

The raw command and captured output are not included in the INFO/AUDIT message or metadata. The SHA-256 digest uses `MessageDigest` and UTF-8. Captured output remains available only in the in-memory tool response after a successfully executed allowlisted command.

## Build and test results

- `:1.21.11:test --tests com.riceawa.llm.function.CommandExecutionPolicyTest`: PASS, 8/8.
- `:1.19:build`: PASS.
- `:1.21.11:build`: PASS.
- IDE diagnostics for all changed Java files: none.
- `git diff --check`: PASS.
- Default tool definitions exclude `execute_command` because the default configuration disables it and `FunctionRegistry` filters disabled functions.

## In-game smoke test availability

No Minecraft client/server smoke test was run in this environment. Validation is limited to focused policy tests and the representative 1.19/1.21.11 builds. A follow-up in-game check should explicitly enable the feature, allowlist a safe command such as `list`, confirm player-source identity/permission behavior, and inspect the audit log metadata.

## References consulted

- Repository instructions: `CLAUDE.md`.
- Minecraft version compatibility assessment: `docs/api/Notable_Minecraft_changes.md` (1.19 Java 17 and 1.21.11 command/mapping context).
- Fabric documentation: https://docs.fabricmc.net/develop/commands/basics — confirmed `CommandSourceStack` represents the actual initiating source and player-specific behavior must validate a player source.
- Existing compatibility APIs: `src/main/java/com/riceawa/llm/compat/CommandCompat.java` and `PermissionCompat.java`.

## Self-review

- Checked the final diff manually and ran `git diff --check`.
- Confirmed no diagnostics in changed Java files.
- Confirmed `ExecuteCommandFunction` contains the player-created source and `CommandCompat.executeCommand`, with no console-source creation, blacklist fallback, or `System.out`/`System.err` calls.
- Confirmed both representative version targets compile and test successfully.

## Concerns

The only remaining concern is the unavailable in-game smoke test noted above. Temporary JDKs were downloaded outside the repository solely to satisfy Gradle’s explicit Eclipse Temurin toolchain requirement; no build/toolchain configuration files were changed.

## Review remediation: security, limits, results, and audit schemas

### Findings addressed

1. **Raw `execute_command` output could enter the INFO LLM request log.** `ExecuteCommandFunction` still keeps captured output for the immediate in-memory player-facing result, but `ToolCallHandler` now converts successful `execute_command` results into a summary built only from `command_root` and `result_code` before it adds a TOOL message or a legacy context message. Thus the next OpenAI request, its `messages`, and its `raw_request_json` cannot contain captured command output. This is intentionally narrow: it does not redesign the general LLM logging path scheduled for Task 9. No raw command or output was added to INFO/AUDIT metadata.
2. **Whitespace-padded inputs bypassed command length enforcement.** `CommandExecutionPolicy.evaluate` now measures the original non-null supplied string before trimming or slash normalization. The configured maximum remains inclusive: exactly 256 original characters are accepted where otherwise valid; 257, including whitespace padding, is rejected with `too_long`.
3. **1.21.11 command auditing used a synthetic success code.** `CommandCompat` now executes the supported `performPrefixedCommand` path with a source result callback and returns a positive result only when the callback reports success. A callback failure, no callback completion, or a zero result produces `0`; exceptions are not treated as command rejection. The older nodes retain Brigadier dispatcher return-code behavior.
4. **`execute_command` emitted an extra generic tool audit record.** `FunctionRegistry` now reserves `execute_command` for its command-specific six-field audit and retains generic auditing for all other functions.

### TDD evidence

Each behavioral change was introduced with a focused failing test before its implementation:

- `:1.21.11:test --tests com.riceawa.llm.function.CommandExecutionPolicyTest` was RED with `rejectsWhitespacePaddedCommandsLongerThanTheConfiguredLimit` failing at line 73; after the original-input length check it was GREEN.
- `:1.21.11:test --tests com.riceawa.llm.command.ToolCallHandlerTest` was RED with `NoSuchMethodException` for the missing summary boundary; after adding the safe TOOL/context summary it was GREEN.
- `:1.21.11:test --tests com.riceawa.llm.compat.CommandCompatTest` was RED at compile time because `resultCodeForCallback` did not exist; after adding callback-based result interpretation it was GREEN. An intermediate compile error caused by a missing `CommandSourceStack` import was immediately corrected before the GREEN run.
- `:1.21.11:test --tests com.riceawa.llm.function.FunctionRegistryAuditPolicyTest` was RED at compile time because `shouldAuditGeneric` did not exist; after extracting the exclusion policy it was GREEN.

### Final validation

```bash
JAVA_HOME=/tmp/gradle-init/temurin-21 \
PATH=/tmp/gradle-init/temurin-21/bin:$PATH \
./gradlew --project-dir /workspaces/LumiChat/.worktrees/multiversion-remediation \
  -Dorg.gradle.java.installations.paths=/tmp/gradle-init/temurin-17,/tmp/gradle-init/temurin-21 \
  :1.21.11:test \
  --tests com.riceawa.llm.function.CommandExecutionPolicyTest \
  --tests com.riceawa.llm.function.FunctionRegistryAuditPolicyTest \
  --tests com.riceawa.llm.command.ToolCallHandlerTest \
  --tests com.riceawa.llm.compat.CommandCompatTest
```

Result: `BUILD SUCCESSFUL`; all four focused suites passed (14 tests total).

```bash
JAVA_HOME=/tmp/gradle-init/temurin-21 \
PATH=/tmp/gradle-init/temurin-21/bin:$PATH \
./gradlew --project-dir /workspaces/LumiChat/.worktrees/multiversion-remediation \
  -Dorg.gradle.java.installations.paths=/tmp/gradle-init/temurin-17,/tmp/gradle-init/temurin-21 \
  :1.19:build :1.21.11:build

git diff --check
```

Result: both representative builds were `BUILD SUCCESSFUL` (30 actions); `git diff --check` passed. IDE diagnostics were clean for the changed production Java files.

### Compatibility and log-safety reasoning

- The 1.21.11 Mojang API exposes `CommandSourceStack.withCallback(CommandResultCallback)` and void `Commands.performPrefixedCommand`. The callback supplies the real `(success, result)` result, so it replaces the prior unconditional synthetic `1`. Stonecutter conditionals keep `Atomic*` and callback code out of the 1.19 source; the 1.19 generated source continues to return Brigadier's `dispatcher.execute` code. Both nodes compiled in final validation.
- Raw output still exists only in the in-memory function result/data for the current player interaction. Before a recursive or legacy follow-up LLM request, the handler replaces it with `命令执行成功: <root> (返回码: <code>)`. The focused handler tests use `secret command output` and assert it is absent from the tool/context-safe content, while ordinary function results remain unchanged.
- The command-specific `execute_command` audit remains exactly `actor_uuid`, `command_root`, `command_sha256`, `result_code`, `duration_ms`, and `success`; generic `Tool execution` auditing is skipped solely for this function.

### Remaining concerns

No in-game command smoke test was possible in this environment. The callback behavior is validated by API inspection, focused interpretation tests, and both representative builds; a live server check should still enable the feature, allowlist `list`, verify accepted and rejected commands, and inspect the six-field audit event.
