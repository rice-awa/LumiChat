# Tasks 13–15 read-only reconnaissance

No source edits or Gradle builds were performed during reconnaissance. API signatures were checked against existing Loom/Mojang mapping jars and `docs/api/Notable_Minecraft_changes.md`.

## Task 13

- Player lookup call sites: `InventoryFunction`, `PlayerStatsFunction`, `SendMessageFunction`, two in `TeleportPlayerFunction`, and an omitted sixth site in `PlayerEffectsFunction`.
- Breakpoints: player lookup follows the plan at `>=1.21.11`; dimension key uses `identifier()` at `>=1.21.11` and `location()` earlier; on-ground uses `onGround()` at `>=1.20` and `isOnGround()` on 1.19.
- `PlayerCompat` should expose `getPlayerByName(server, name)` and `isOnGround(player)`; `DimensionCompat` should expose stable `String` ID/display-name methods.
- The plan must add `PlayerEffectsFunction` to Task 13. Its whole-file `//?` zero-result gate is premature because Teleport/WorldInfo/PlayerEffects still contain Task 14/15 branches. Gate only the Task 13 APIs, leaving the global gate for Task 15/18.

## Task 14

- Breakpoints: registry lookup/entity creation/teleport at 1.21.2; Identifier rename at 1.21.11.
- `RegistryCompat` must call `containsKey` before `get`/`getValue`; BLOCK and ENTITY_TYPE are defaulted registries and unknown IDs otherwise fall back instead of returning null.
- Recommended APIs: `RegistryCompat.getBlock/getEntityType`, `EntityCompat.create`, `TeleportCompat.teleport`.
- Add a direct `:1.21.2:build` boundary check alongside the planned representatives.

## Task 15

- The plan omits `FunctionRegistry`, which still calls `EntityHelper.getDayTime`; add it to Files and staging scope if the EntityHelper time wrapper is removed as planned.
- Breakpoints: effect Holder at 1.20.5, minimum height at 1.21.2, respawn data at 1.21.9, Identifier at 1.21.11, clock/weather at 26.1.
- 26.x time must use the world's default clock, not hard-coded `WorldClocks.OVERWORLD`, so the End remains correct.
- Effect IDs must be canonical registry IDs rather than Holder `toString()`; strip `minecraft:` only for the existing vanilla Chinese-name lookup.
- Surface height has the same signature across checked versions and does not need a branch.
- Add direct `:1.21.9:build` and retain 26.1/26.2 boundary checks.

All new shared compat sources must remain Java 17 compatible. Tasks 13–15 must execute serially after Task 12 because they share business files.
