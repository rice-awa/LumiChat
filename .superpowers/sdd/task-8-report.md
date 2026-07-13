# Task 8 Report — 收紧玩家交互函数与 Wiki 端点安全

## Status

Completed and committed after validation. In-game smoke testing was unavailable in this headless development environment; this is the only remaining concern.

## Files changed

- `src/main/java/com/riceawa/llm/function/WikiEndpointPolicy.java` — new fail-closed HTTPS endpoint policy and non-redirecting OkHttp client builder.
- `src/main/java/com/riceawa/llm/config/ConfigDefaults.java` — default Wiki allowlist contains exactly `mcwiki.rice-awa.top`.
- `src/main/java/com/riceawa/llm/config/LLMChatConfig.java` — persists and defensively exposes `wikiAllowedHosts`.
- `src/main/java/com/riceawa/llm/function/impl/WikiSearchFunction.java`
- `src/main/java/com/riceawa/llm/function/impl/WikiPageFunction.java`
- `src/main/java/com/riceawa/llm/function/impl/WikiBatchPagesFunction.java` — validate configured base URL, use `HttpUrl` path/query builders, and disable all redirect following.
- `src/main/java/com/riceawa/llm/function/impl/SendMessageFunction.java` — restrict message content, message type, recipient access, and broadcast semantics.
- `src/main/java/com/riceawa/llm/function/impl/TeleportPlayerFunction.java` — expose only to OPs.
- `src/test/java/com/riceawa/llm/function/WikiEndpointPolicyTest.java` — focused endpoint and interaction-policy coverage.

`progress.md` was deliberately not changed by this task and was excluded from the commit.

## TDD evidence

### RED

1. `JAVA_HOME=/tmp/temurin21 PATH=/tmp/temurin21/bin:$PATH ./gradlew :1.21.11:test --tests com.riceawa.llm.function.WikiEndpointPolicyTest --max-workers=1`
   - Initial result: failed at test compilation because `WikiEndpointPolicy` did not exist.
2. After the endpoint-policy implementation, the redirect test failed because its synthetic `Response` lacked a body; corrected the test fixture without weakening production code.
3. After adding direct interaction-policy assertions, the focused test failed at test compilation because the required policy methods did not exist.

### GREEN

`JAVA_HOME=/tmp/temurin21 PATH=/tmp/temurin21/bin:$PATH ./gradlew :1.21.11:test --tests com.riceawa.llm.function.WikiEndpointPolicyTest --max-workers=1 -Dorg.gradle.java.installations.paths=/tmp/temurin17,/tmp/temurin21`

- Result: `BUILD SUCCESSFUL`; 7 focused tests passed.

## Security decisions and evidence

### Wiki / SSRF

- Accept only a parseable `https` `HttpUrl` with no username/password, port 443, a non-IP host, and an IDN ASCII-normalized exact match in a nonempty allowlist.
- Reject HTTP, userinfo, IPv4/IPv6 literals, explicit non-default ports, unknown hosts, empty allowlists, and suffix/subdomain spoofing such as `mcwiki.rice-awa.top.evil.test`.
- The default allowlist is exactly `mcwiki.rice-awa.top`.
- All three Wiki functions construct paths with `HttpUrl.newBuilder().addPathSegments(...)`; page names and query values use `addPathSegment` / `addQueryParameter`, eliminating URL string concatenation and form encoding.
- Wiki clients are created through `WikiEndpointPolicy.newSecureClientBuilder()` with both `.followRedirects(false)` and `.followSslRedirects(false)`. A 3xx is returned as the existing generic HTTP error and the `Location` header is not used or exposed.
- Review search found no new `System.out`, `System.err`, or raw endpoint/redirect logging in Task 8 files.

### Player interaction

- `send_message.message` is enforced at 1–512 characters in schema and execution.
- `message_type` accepts exactly `chat`, `system`, or `actionbar`.
- Omitted/blank target is self-only. `target=all` is an explicit OP-only broadcast. A regular player cannot target another player; missing targets return the fixed `目标玩家不可用` error without disclosing player-specific details.
- `teleport_player.hasPermission` is now OP-only, so the registry omits it for ordinary players. Existing coordinate Y-range, dimension validation, landing, and chunk behavior were intentionally unchanged.

### Task 7 preservation

- `executeCommandAllowlist` and `executeCommandMaxLength` behavior is untouched. The shared configuration additions are limited to `wikiAllowedHosts`.

## Build and test validation

- First build attempt with Temurin 17 failed before compilation because Fabric Loom 1.15.5 requires a Java 21 Gradle runtime. This was an environment/runtime constraint, not a source failure.
- Installed the repository-required toolchains locally for validation:
  - Temurin 17.0.19 at `/tmp/temurin17`
  - Temurin 21.0.11 at `/tmp/temurin21`
- Representative matrix command:

  ```bash
  JAVA_HOME=/tmp/temurin21 PATH=/tmp/temurin21/bin:$PATH \
    ./gradlew :1.19:build :1.20.6:build :1.21.11:build \
    --max-workers=1 \
    -Dorg.gradle.java.installations.paths=/tmp/temurin17,/tmp/temurin21
  ```

  Result: `BUILD SUCCESSFUL` (48 tasks; 27 executed, 21 up-to-date).

- `git diff --check`: passed.
- VS Code diagnostics: no diagnostics in `WikiEndpointPolicy`, `SendMessageFunction`, `TeleportPlayerFunction`, or `WikiEndpointPolicyTest`.

## References consulted

- `docs/api/Notable_Minecraft_changes.md`: confirmed relevant target Java/version boundaries (1.19 Java 17 and 1.20.5+ Java 21); this task does not introduce Minecraft API changes.
- [OkHttp `followRedirects`](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-ok-http-client/-builder/follow-redirects.html): redirect following is enabled by default, so it must be explicitly disabled.
- [OkHttp `HttpUrl.Builder.addPathSegments`](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-http-url/-builder/add-path-segments.html): used the structured `HttpUrl` builder for path construction.

## Self-review

- Checked exact host matching and IDN normalization are performed before requests.
- Checked every Wiki requester uses the central policy and a non-redirecting client.
- Checked userinfo, IP literals, explicit alternative ports, empty allowlist, unknown host, and subdomain spoof cases have focused tests.
- Checked regular-player message and teleport restrictions are enforced at execution/registry permission boundaries.
- Checked no unrelated `progress.md` modification was staged or committed.

## Remaining concerns

- In-game smoke validation was unavailable: manually verify on a running server that non-OP tool definitions omit `teleport_player`, non-OP calls to `send_message` cannot target another player, and a valid Wiki query against the default endpoint succeeds.

## Review remediation — alternate numeric IPv4 forms

An independent review found that `WikiEndpointPolicy.isIpLiteral` rejected conventional IPv4 literals but did not reject all legacy numeric IPv4 spellings accepted by the URL parser. This could permit a hostname allowlist entry for an alternate numeric representation to pass validation.

### Change

- Extended the existing numeric IPv4 parser to treat a leading `0` as an octal marker (after handling `0x` / `0X` hexadecimal markers), using the correct part-size limits for one-, two-, three-, and four-part IPv4 forms.
- Kept the policy fail-closed: malformed numeric parts and values outside their form-specific ranges are not classified as IP literals, while parser-supported valid numeric address spellings are rejected before exact allowlist matching.
- Added focused regressions for `127.1`, `127.0.1`, `2130706433`, hexadecimal `0x7f000001`, single-part octal `017700000001`, and dotted octal `0377.0377.0377.0377`. Existing DNS/IDN exact-match and default-host coverage remains unchanged.

### TDD evidence

- **RED:** With the new regressions and the pre-remediation production code, `:1.21.11:test --tests com.riceawa.llm.function.WikiEndpointPolicyTest --max-workers=1` failed because `https://017700000001` was accepted when it was allowlisted.
- **GREEN:** After the minimal Java 17-compatible radix correction, the same focused command completed successfully with all 8 tests passing.

### Validation

- `:1.19:build --max-workers=1`: passed.
- The requested remaining representative builds and `git diff --check` are recorded with this remediation commit.
