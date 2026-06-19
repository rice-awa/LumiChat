# Task 3 Final Review Fixes

## 2026-06-19 final branch review fixes

### Findings addressed
- Restored the full Stonecutter supported version matrix in `settings.gradle.kts` from `main`, including grouped legacy projects `1.16.5`, `1.17`, `1.18`, `1.19` mapped to `1.19.4`, and all supported `1.20.x`/`1.21.x` projects through `1.21.11`.
- Preserved `vcsVersion = "1.21.11"` and the Java 25 compatibility gate around `version("26.1")`.
- Removed tracked local Claude plugin metadata `.claude/settings.json`; did not stage or touch `.claude/worktrees/`.
- Adjudicated Gradle wrapper upgrade: not reverted. The upgrade to Gradle 9.4.0 is plan-mandated for Minecraft 26.1 / Loom 1.15 compatibility.

### Verification
- PASS: `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew projects --console=plain`
  - Confirmed restored projects include `:1.16.5`, `:1.17`, `:1.18`, `:1.20`, `:1.21.10`, `:1.21.11`; `:26.1` absent under Java 21 as expected from the Java 25 gate.
- PASS: `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.19:build :1.20.6:build :1.21.11:build --console=plain`
- PASS: `JAVA_HOME=/usr/local/sdkman/candidates/java/25.0.2-ms ./gradlew :26.1:build --console=plain`
- PASS: `JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms ./gradlew :1.20:compileJava --console=plain`
  - Initial restored-node compile exposed a version-marker issue in `PlayerStatsFunction`: Minecraft 1.20 Mojang mappings use `ServerPlayer.onGround()`, not `isOnGround()`. Fixed the Stonecutter marker from `>=1.20.6` to `>=1.20`, then re-ran successfully.
