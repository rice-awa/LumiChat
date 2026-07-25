# Task 11 Report: Recoverable HTTP Retry

## Changes

- Added the same-version `MockWebServer` 4.12.0 test dependency.
- Added `HttpStatusException`, exposing `statusCode()` and a sanitized, 256-character bounded `responseSummary()`; its internal retry-after delay is package-private.
- Added `RetryPolicy` for the explicit recoverable statuses (429/502/503/504), exponential delay (`base * multiplier^(attempt - 1)`), injected bounded jitter `[0.5, 1.5)`, `Retry-After` maximum selection, and a 30-second cap.
- Updated `OpenAIService` to log failure details before either returning an unrecoverable 4xx response or throwing the structured recoverable error. It now parses numeric and RFC 1123 `Retry-After`, retries only `IOException` and the structured recoverable statuses, and restores the interrupt flag before ending an interrupted retry.
- Added focused policy and MockWebServer tests covering status eligibility, bounded jitter/cap, Retry-After precedence, 429 → 503 → 200 retry success, 400 no-retry, and safely truncated retry-exhaustion errors. Test base delay is zero so HTTP tests do not wait.

## Documentation consulted

Before implementing, checked MDN's HTTP `Retry-After` reference: it defines both non-negative delta-seconds and HTTP-date forms, used with retryable responses such as 429 and 503.

- https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Retry-After
- https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/429
- https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/503

## Test commands and results

- Initial RED attempt: `./gradlew :1.21.11:test --tests 'com.riceawa.llm.service.*Retry*'` could not start because the environment's default Gradle JVM was Java 11. Retried with the preinstalled Microsoft Java 21 but Gradle rejected it because the project requires Eclipse Temurin. This was an environment/toolchain failure before tests could compile, not a behavioral assertion failure.
- `JAVA_HOME=/tmp/temurin-21 PATH=/tmp/temurin-21/bin:$PATH ./gradlew :1.21.11:test --tests 'com.riceawa.llm.service.*Retry*'` — PASS (6 tests, 0 failures/errors; rerun after final formatting adjustment).
- `JAVA_HOME=/tmp/temurin-21 PATH=/tmp/temurin-21/bin:$PATH ./gradlew :1.21.11:build` — PASS.
- `JAVA_HOME=/tmp/temurin-21 PATH=/tmp/temurin-21/bin:$PATH ./gradlew -Dorg.gradle.java.installations.paths=/tmp/temurin-17,/tmp/temurin-21 :1.19:build` — PASS.

## Self-review

- Verified retry eligibility is exactly 429/502/503/504 plus explicit `IOException`; 400 returns after one request.
- Verified retry delays use injected policy jitter and cap at 30 seconds; a valid `Retry-After` contributes through the greater of server and local delays.
- Verified response errors use the new sanitized, bounded summary rather than the full response body. Existing optional raw-response logging behavior and its existing sanitization boundaries remain unchanged.
- Verified interrupted sleeping restores the thread interrupt flag and exits the retry loop by throwing `InterruptedException`.
- Ran `git diff --check`; no whitespace errors.

## Follow-up remediation (post-review)

### Changes

- Added a package-private `OpenAIService` retry-sleeper seam. Production construction still uses `Thread.sleep`; the test constructor records the selected delay without waiting.
- Added an end-to-end MockWebServer test that queues `429` with `Retry-After: 2`, then `200`, invokes `OpenAIService.chat`, and asserts two requests plus a selected delay of at least 2,000 ms while retry base delay remains zero.
- Updated the HTTP retry test fixture to save and restore the original Fabric Loader `configDir` and every modified retry setting (`enableRetry`, max attempts, base delay, multiplier) in teardown.

### Follow-up test commands and results

- `JAVA_HOME=/tmp/temurin-21 PATH=/tmp/temurin-21/bin:$PATH ./gradlew :1.21.11:test --tests 'com.riceawa.llm.service.*Retry*'` — PASS (7 tests, 0 failures/errors).
- `JAVA_HOME=/tmp/temurin-21 PATH=/tmp/temurin-21/bin:$PATH ./gradlew :1.21.11:build` — PASS.
- `JAVA_HOME=/tmp/temurin-21 PATH=/tmp/temurin-21/bin:$PATH ./gradlew -Dorg.gradle.java.installations.paths=/tmp/temurin-17,/tmp/temurin-21 :1.19:build` — PASS.
- `git diff --check` — PASS.

### Follow-up self-review

- The end-to-end assertion observes the actual delay selected after parsing the MockWebServer `Retry-After` header; it does not call `RetryPolicy` directly and does not sleep.
- Test cleanup restores both global loader state and all retry settings even after the server is shut down.
- No unrelated files were changed; the existing unrelated `progress.md` modification and version log directories remain untouched.

### Follow-up concerns

- The test uses a package-private constructor seam solely to avoid a real two-second wait; the public production constructors and runtime behavior remain unchanged.
