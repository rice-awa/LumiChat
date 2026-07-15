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

## 2026-07-13 Remediation 5: sanitize follow-up tool-call arguments

### Finding addressed and data flow

A successful `execute_command` result was already summarized before creating the TOOL response, but `appendToolExchange` still copied the original assistant-side `LLMMessage.ToolCall`. Its raw JSON `arguments` included the command. `callLLMWithFunctionResult` passes `chatContext.getMessages()` to `OpenAIService`, whose `buildRequestBody` serializes assistant `metadata.toolCall` as `tool_calls[].function.arguments`; that request body is also written as `raw_request_json` at INFO. Thus raw command text could persist in the recursive follow-up request and log even after TOOL result-content sanitization.

`ToolCallHandler.appendToolExchange` now calls `safeFollowUpToolCall`. For `execute_command`, the stored assistant metadata retains the original function name and `tool_call_id`, but substitutes the fixed argument JSON `{}`. It neither retains the input JSON nor the command. Ordinary functions return their original `ToolCall` object unchanged, preserving existing request behavior and recursive tool-call protocol. This is intentionally a narrow context-boundary fix; it does not redesign Task 9's general logging behavior.

### TDD evidence

RED was run after adding the focused regression that invokes `appendToolExchange` and inspects the actual stored assistant context metadata:

```bash
JAVA_HOME=/tmp/gradle-init/temurin-21 \
PATH=/tmp/gradle-init/temurin-21/bin:$PATH \
./gradlew --project-dir /workspaces/LumiChat/.worktrees/multiversion-remediation \
  --max-workers=1 \
  -Dorg.gradle.java.installations.paths=/tmp/gradle-init/temurin-17,/tmp/gradle-init/temurin-21 \
  :1.21.11:test --tests com.riceawa.llm.command.ToolCallHandlerTest
```

Result: `BUILD FAILED` as expected; 5 tests completed and `sanitizesExecuteCommandArgumentsInTheFollowUpToolExchange` failed at `ToolCallHandlerTest.java:47`, because the stored assistant metadata still contained the raw command arguments.

GREEN after the minimal `safeFollowUpToolCall` implementation:

```bash
JAVA_HOME=/tmp/gradle-init/temurin-21 \
PATH=/tmp/gradle-init/temurin-21/bin:$PATH \
./gradlew --project-dir /workspaces/LumiChat/.worktrees/multiversion-remediation \
  --max-workers=1 \
  -Dorg.gradle.java.installations.paths=/tmp/gradle-init/temurin-17,/tmp/gradle-init/temurin-21 \
  :1.21.11:test --tests com.riceawa.llm.command.ToolCallHandlerTest
```

Result: `BUILD SUCCESSFUL`; the suite passed 5/5. The regression uses `op SensitivePlayer --secret=never-log-this`, asserts that actual assistant context metadata contains the same name and ID with `{}`, and proves it does not retain the raw command. A companion regression verifies ordinary `get_time` arguments preserve the original `ToolCall`.

### Final validation

```bash
JAVA_HOME=/tmp/gradle-init/temurin-21 \
PATH=/tmp/gradle-init/temurin-21/bin:$PATH \
./gradlew --project-dir /workspaces/LumiChat/.worktrees/multiversion-remediation \
  --max-workers=1 \
  -Dorg.gradle.java.installations.paths=/tmp/gradle-init/temurin-17,/tmp/gradle-init/temurin-21 \
  :1.21.11:test \
  --tests com.riceawa.llm.command.ToolCallHandlerTest \
  --tests com.riceawa.llm.function.CommandExecutionPolicyTest \
  --tests com.riceawa.llm.compat.CommandCompatTest \
  --tests com.riceawa.llm.function.FunctionRegistryAuditPolicyTest
```

Result: `BUILD SUCCESSFUL`; XML results were ToolCallHandler 5/5, CommandExecutionPolicy 9/9, CommandCompat 1/1, and FunctionRegistryAuditPolicy 1/1 (16 tests total, zero failures/errors/skips).

```bash
JAVA_HOME=/tmp/gradle-init/temurin-21 \
PATH=/tmp/gradle-init/temurin-21/bin:$PATH \
./gradlew --project-dir /workspaces/LumiChat/.worktrees/multiversion-remediation \
  --max-workers=1 \
  -Dorg.gradle.java.installations.paths=/tmp/gradle-init/temurin-17,/tmp/gradle-init/temurin-21 \
  :1.19:build :1.21.11:build

git -C /workspaces/LumiChat/.worktrees/multiversion-remediation diff --check
```

Result: both representative builds were `BUILD SUCCESSFUL` (30 actionable tasks; 5 executed, 25 up-to-date); `git diff --check` passed. Java diagnostics were clean for the changed production and test files. The 1.19 build verifies the shared code remains Java 17 compatible.

### Remaining limitation

No in-game smoke test was available. A live server check should enable and allowlist `execute_command`/`list`, trigger a recursive command call containing sensitive arguments, then verify that the following INFO `raw_request_json` emits `{}` for the assistant tool-call arguments rather than the command text.

## 2026-07-15 Task 7 correction: return complete command output to the LLM

### Files changed

- `src/main/java/com/riceawa/llm/config/ConfigDefaults.java`
- `src/main/java/com/riceawa/llm/config/LLMChatConfig.java`
- `src/main/java/com/riceawa/llm/command/ToolCallHandler.java`
- `src/test/java/com/riceawa/llm/command/ToolCallHandlerTest.java`

### Behavior

- Added serialized `executeCommandReturnFullOutput`, which defaults to `true`; absent legacy configuration receives that default. Its getter and setter save the administrator-selected value.
- The two recursive tool-exchange paths and legacy tool-call path now pass the active `LLMChatConfig` into `toolResultContent`.
- For a successful `execute_command`, `ToolCallHandler.commandExecutionSummary` appends `FunctionResult.data.output` only while `executeCommandReturnFullOutput` is enabled. With the setting disabled, the existing `命令执行成功: <root> (返回码: <code>)` summary is retained. Other function results are unchanged.
- The correction does not alter `enableExecuteCommand`, which remains disabled by default; it does not change the allowlist, initiating-player `CommandSourceStack`, or command audit implementation. `ExecuteCommandFunction.audit` continues to emit only actor UUID, command root, command SHA-256, result code, duration, and success. Raw command text and command output are not written to audit logs. The Task 7 `{}` sanitization of recursive `execute_command` arguments remains intact, so raw command arguments do not enter follow-up request/INFO logs.

### Tests and builds

The available Java 21/init-script setup was used without changing repository build configuration. Eclipse Temurin 17/21 toolchains were installed under `/tmp` only because the host initially lacked the Java 17 compiler required by the 1.19 node.

```bash
JAVA_HOME=/tmp/lumichat-jdk21 \
PATH=/tmp/lumichat-jdk21/bin:$PATH \
./gradlew --init-script /tmp/lumichat-adoptium-jdk.gradle --max-workers=1 \
  -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdk17,/tmp/lumichat-jdk21 \
  :1.21.11:test --tests com.riceawa.llm.command.ToolCallHandlerTest
```

Result: `BUILD SUCCESSFUL`; 5 tests, 0 failures/errors/skips.

```bash
JAVA_HOME=/tmp/lumichat-jdk21 \
PATH=/tmp/lumichat-jdk21/bin:$PATH \
./gradlew --init-script /tmp/lumichat-adoptium-jdk.gradle --max-workers=1 \
  -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdk17,/tmp/lumichat-jdk21 \
  :1.21.11:test --tests com.riceawa.llm.function.CommandExecutionPolicyTest
```

Result: `BUILD SUCCESSFUL`; 9 tests, 0 failures/errors/skips.

```bash
JAVA_HOME=/tmp/lumichat-jdk21 \
PATH=/tmp/lumichat-jdk21/bin:$PATH \
./gradlew --init-script /tmp/lumichat-adoptium-jdk.gradle --max-workers=1 \
  -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdk17,/tmp/lumichat-jdk21 \
  :1.19:build :1.21.11:build
```

Result: `BUILD SUCCESSFUL`; 30 actionable tasks (8 executed, 22 up-to-date). `git diff --check` passed during self-review.

### Remaining concern

No live provider/server smoke test was available. A live check should enable both full logging flags, issue one allowlisted execute_command call and one get_time call, then inspect that execute_command command/output are absent from both serialized response fields while get_time data remains visible and the in-memory execute_command result remains complete.

## 2026-07-15 Important review fix: legacy tool_call and response content logging boundaries

### Review findings addressed

- `sanitizeLlmLogContent` now redacts both modern plural `tool_calls` and the legacy singular `assistant.tool_call` supported by `OpenAIService`. It also covers the corresponding `assistant.function_call` shape when present. Only calls whose function/name is exactly `execute_command` have arguments replaced; `get_time` arguments remain visible in configured full logging.
- `LLMResponseLogEntry.Builder.content(String, boolean, int)` now uses `sanitizeLlmLogContent`, closing the derived `content` bypass for protocol JSON that contains execute_command output. Both `raw_response_json` and `content` therefore receive command-aware structural redaction.
- The full in-memory LLM tool result remains unchanged and still includes complete command output when `executeCommandReturnFullOutput=true`; only serialized log fields are redacted. Command execution remains disabled by default, allowlist/player-source behavior remains unchanged, and audit fields remain metadata-only.

### Regression coverage

`LLMLogSanitizerTest` now covers: legacy singular `tool_call`, `function_call` compatibility, modern `tool_calls`, response `content` plus `raw_response_json`, and same-payload `get_time` arguments/content preservation. The tests assert command and output markers are absent while non-command tool data remains present.

### Validation results

```bash
JAVA_HOME=/tmp/lumichat-jdk21 \
PATH=/tmp/lumichat-jdk21/bin:$PATH \
./gradlew --init-script /tmp/lumichat-adoptium-jdk.gradle --max-workers=1 \
  -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdk17,/tmp/lumichat-jdk21 \
  :1.21.11:test --tests com.riceawa.llm.logging.LLMLogSanitizerTest
```

Result: `BUILD SUCCESSFUL`; 16 tests, 0 failures/errors/skips.

```bash
JAVA_HOME=/tmp/lumichat-jdk21 \
PATH=/tmp/lumichat-jdk21/bin:$PATH \
./gradlew --init-script /tmp/lumichat-adoptium-jdk.gradle --max-workers=1 \
  -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdk17,/tmp/lumichat-jdk21 \
  :1.21.11:test --tests com.riceawa.llm.command.ToolCallHandlerTest \
  :1.21.11:test --tests com.riceawa.llm.function.CommandExecutionPolicyTest \
  :1.19:build :1.21.11:build
```

Result: `BUILD SUCCESSFUL`; ToolCallHandlerTest 5/5, CommandExecutionPolicyTest 9/9, and both representative builds succeeded (30 actionable tasks, 16 executed, 14 up-to-date). `git diff --check` passed.


### Review finding and scope rationale

The correction's full-output behavior was correct at the immediate `ToolCallHandler` tool-message boundary, but full request/response logging created a second data path. `OpenAIService` serializes the recursive assistant tool-call arguments before `safeFollowUpToolCall` can affect a later context copy, and it also serializes the TOOL message containing the captured output. The existing Task 9 sanitizer removed credentials but intentionally retained ordinary full content, so raw execute_command command arguments and output could still enter INFO `raw_request_json`, `messages[].content`, or response content when both full-body switches were enabled.

The smallest secure change crosses the existing Task 9 logging boundary only: `LLMLogSanitizer` now provides `sanitizeLlmLogContent`, and both request/response DTOs use it for their explicitly enabled raw-body fields. The normal `sanitizeJson`/`sanitizeContent` behavior remains unchanged for non-command data. This is intentionally a Task 9 boundary hardening, not a change to execution policy or the user-facing tool result.

### Files changed for the review fix

- `src/main/java/com/riceawa/llm/logging/LLMLogSanitizer.java`
- `src/main/java/com/riceawa/llm/logging/LLMRequestLogEntry.java`
- `src/main/java/com/riceawa/llm/logging/LLMResponseLogEntry.java`
- `src/test/java/com/riceawa/llm/logging/LLMLogSanitizerTest.java`

### Security behavior

- In log-only JSON content, assistant `tool_calls[].function` entries named `execute_command` have `arguments` replaced with `[REDACTED execute_command arguments]`, regardless of the full request-body setting.
- TOOL messages named `execute_command` have `content` replaced with `[REDACTED execute_command output]`. Legacy assistant context content beginning with `调用了函数 execute_command，结果：` is also replaced at the log boundary.
- The same structural redaction applies to response raw JSON when a provider echoes tool calls or output-like TOOL messages. Other tool/function content continues through the existing credential sanitizer and configured truncation path.
- `ToolCallHandler` still returns the complete captured output in the in-memory LLM tool message when `executeCommandReturnFullOutput=true`; this fix changes only serialized INFO log data. `enableExecuteCommand=false`, allowlist checks, player-created `CommandSourceStack`, safe follow-up `{}` arguments, and the six-field audit schema are unchanged.

### Regression tests and results

```bash
JAVA_HOME=/tmp/lumichat-jdk21 \
PATH=/tmp/lumichat-jdk21/bin:$PATH \
./gradlew --init-script /tmp/lumichat-adoptium-jdk.gradle --max-workers=1 \
  -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdk17,/tmp/lumichat-jdk21 \
  :1.21.11:test --tests com.riceawa.llm.logging.LLMLogSanitizerTest
```

Result: `BUILD SUCCESSFUL`; 15 sanitizer tests, 0 failures/errors/skips. The added tests enable the DTO full-content overloads and assert raw command/output are absent from serialized request and response log JSON while redaction markers remain.

```bash
JAVA_HOME=/tmp/lumichat-jdk21 \
PATH=/tmp/lumichat-jdk21/bin:$PATH \
./gradlew --init-script /tmp/lumichat-adoptium-jdk.gradle --max-workers=1 \
  -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdk17,/tmp/lumichat-jdk21 \
  :1.21.11:test --tests com.riceawa.llm.command.ToolCallHandlerTest
```

Result: `BUILD SUCCESSFUL`; existing Task 7 handler suite passed 5/5, including full output enabled, summary-only mode, and ordinary function behavior.

```bash
JAVA_HOME=/tmp/lumichat-jdk21 \
PATH=/tmp/lumichat-jdk21/bin:$PATH \
./gradlew --init-script /tmp/lumichat-adoptium-jdk.gradle --max-workers=1 \
  -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdk17,/tmp/lumichat-jdk21 \
  :1.21.11:test --tests com.riceawa.llm.function.CommandExecutionPolicyTest \
  :1.19:build :1.21.11:build
```

Result: `BUILD SUCCESSFUL`; CommandExecutionPolicyTest passed 9/9 and both required representative builds completed successfully (30 actionable tasks, 16 executed, 14 up-to-date). `git diff --check` passed.

### Concerns

No live OpenAI-compatible provider/server smoke test was available, so the logging-path regression is validated at the exact DTO/sanitizer boundary used by `OpenAIService`, with both full-body overloads enabled. A live check should enable full request/response logging, issue an allowlisted `execute_command` call with distinctive command/output markers, verify those markers are present in the in-memory tool result but absent from INFO logs, and inspect the unchanged six-field audit event.

## Final correction hardening — 2026-07-15

### Review findings addressed

- Added request-level `containsExecuteCommand` tracking in `OpenAIService`. When the current or retained context contains an `execute_command` request/result, full request message content and `raw_request_json` are disabled even if `logFullRequestBody` is enabled; role, length, hash, and irreversible raw-body summaries remain available.
- Response DTOs use the same context to suppress full provider `content` and `raw_response_json`, preventing an ordinary assistant response from echoing captured command output into INFO logs.
- Applied the command-output redaction to both `TOOL` and legacy `FUNCTION` `LLMMessage` summaries.
- Replaced `Stream.toList()` with a Java 8-compatible `ArrayList` copy during recursive JSON traversal so the shared logging source remains compilable for older Minecraft targets.

### Regression coverage

`LLMLogSanitizerTest` now explicitly covers legacy `function_call`, real `FUNCTION` message summaries, ordinary assistant response echo, a follow-up request after an execute-command echo, and preservation of full request/response content for non-command responses. The tests assert that command/output markers do not occur in serialized request or response logs.

### Final validation

```bash
JAVA_HOME=/tmp/lumichat-jdk21 \
PATH=/tmp/lumichat-jdk21/bin:$PATH \
./gradlew -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdk17,/tmp/lumichat-jdk21 \
  --max-workers=1 :1.21.11:test --tests com.riceawa.llm.logging.LLMLogSanitizerTest \
  --rerun-tasks --no-build-cache
```

Result: `BUILD SUCCESSFUL`.

```bash
JAVA_HOME=/tmp/lumichat-jdk21 \
PATH=/tmp/lumichat-jdk21/bin:$PATH \
./gradlew -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdk17,/tmp/lumichat-jdk21 \
  --max-workers=1 :1.21.11:test \
  --tests com.riceawa.llm.command.ToolCallHandlerTest \
  --tests com.riceawa.llm.function.CommandExecutionPolicyTest \
  --rerun-tasks --no-build-cache
```

Result: `BUILD SUCCESSFUL`; ToolCallHandlerTest 5/5 and CommandExecutionPolicyTest 9/9.

```bash
JAVA_HOME=/tmp/lumichat-jdk21 \
PATH=/tmp/lumichat-jdk21/bin:$PATH \
./gradlew -Dorg.gradle.java.installations.paths=/tmp/lumichat-jdk17,/tmp/lumichat-jdk21 \
  --max-workers=1 :1.19:build :1.21.11:build \
  --rerun-tasks --no-build-cache
```

Result: `BUILD SUCCESSFUL`; both representative builds completed successfully. `git diff --check` passed.

### Independent final review

Specification: PASS. Code quality: PASS. Critical/Important/Minor findings: none. The reviewer confirmed that execute-command output remains available to the in-memory LLM tool result, while audit and INFO LLM logs retain only the existing six-field audit schema or safe summaries; non-command full logging remains enabled by configuration. No live provider/server smoke test was available.
