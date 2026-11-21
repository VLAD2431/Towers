# Wave-Specific Loot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure reusable loot entries by ID and select the eligible entries through wave parameters, rolling them once when the final wave ends and the rift closes.

**Architecture:** Keep item construction and chance/amount rules in the existing loot model. Add a catalog keyed by stable IDs and wave-specific ID lists to `WaveConfig`; at final completion, resolve the selected wave configuration to its loot entries and pass them through the existing `RiftLoot.rollAll` flow. Keep `rift-loot` as fallback when the new format is absent.

**Tech Stack:** Java 21, Paper API 1.21+, Maven, JUnit tests, Bukkit YAML configuration.

**Spec:** `docs/superpowers/specs/2026-11-10-wave-specific-loot-design.md`

## Global Constraints

- Use Paper API and Java 21 as defined by `AGENTS.md`.
- Preserve the existing loot building and roll semantics unless required for ID selection.
- Do not perform filesystem or database work on the server main thread; current loot logic is in-memory and server-thread-bound.
- Always run `mvn test-compile` after Java changes.
- Never stage `target/` or `.jar` files.

## Review Focus

- Missing or invalid catalog IDs must not crash config loading; they are ignored with a warning. Test unknown ID resolution.
- Empty wave loot lists must not accidentally cause the legacy global loot to drop when the new format is configured. Test empty list behavior.
- Existing config without the new catalog/selection must still drop legacy `rift-loot` once at final rift closure. Test fallback selection and final-roll call path.
- Intermediate wave completion must not award loot. Test that loot is only passed to the roll path at final completion.
- The eligible catalog must correspond to the configured wave parameters, not unrelated wave types. Test selected wave selection.

---

### Task 1: Parse loot catalog and wave selections

**Files:**
- Modify: `src/main/java/VLADMIX90/towers/wave/WaveConfig.java`
- Modify: `src/main/resources/config.yml`
- Test: `src/test/java/VLADMIX90/towers/wave/WaveConfigLootTest.java`

**Interfaces:**
- Produce `List<RiftLootSpec> lootForWave(String waveTypeId)`; return the legacy `rift-loot` list only when the new catalog/selection format is absent, otherwise return the IDs selected for `waveTypeId` resolved in configured order.
- Preserve `loot()` and `assignGuaranteed(...)` compatibility for existing call sites/tests; adjust guaranteed-drop assignment to use the configured catalog entries when present.

- [ ] **Step 1: Write failing tests** for parsing a catalog entry by ID, resolving configured IDs for one wave, preserving order, ignoring unknown IDs with warning, returning an empty list for an explicitly empty selection, and using legacy `rift-loot` when the new format is absent.
- [ ] **Step 2: Run `mvn -Dtest=WaveConfigLootTest test`** and confirm the new tests fail for missing API/behavior.
- [ ] **Step 3: Implement the catalog and per-wave lookup** in `WaveConfig.java`, preserving existing `RiftLootSpec` fields and roll semantics. Add example catalog and wave-selection entries to `config.yml` without removing the existing fallback example unless converted deliberately.
- [ ] **Step 4: Run `mvn -Dtest=WaveConfigLootTest test`** and confirm all focused tests pass.

### Task 2: Use selected wave loot at final rift closure

**Files:**
- Modify: `src/main/java/VLADMIX90/towers/game/GameManager.java`
- Test: `src/test/java/VLADMIX90/towers/game/GameManagerWaveLootTest.java`

**Interfaces:**
- Consume `WaveConfig.lootForWave(String waveTypeId)` from Task 1.
- Retain current finalization point `finishWaves(UUID id)` and its single call to `RiftLoot.rollAll(...)`.

- [ ] **Step 1: Write failing tests** proving intermediate wave progression does not award loot and final completion rolls only the selected wave's entries once as the rift closes; cover legacy fallback.
- [ ] **Step 2: Run `mvn -Dtest=GameManagerWaveLootTest test`** and confirm the behavior fails before the integration change.
- [ ] **Step 3: Update `finishWaves(UUID id)`** to resolve the selected quest's wave type, obtain its eligible loot list, then retain the existing final-only `RiftLoot.rollAll(...)` and drop location behavior. Ensure guaranteed-drop selection uses the same catalog IDs.
- [ ] **Step 4: Run `mvn -Dtest=GameManagerWaveLootTest test`** and confirm all focused tests pass.

### Task 3: Full verification

**Files:**
- No additional files expected.

- [ ] **Step 1: Run `mvn test-compile`** and confirm successful compilation of main and test sources.
- [ ] **Step 2: Run `mvn test`** and confirm the full suite passes.
- [ ] **Step 3: Review the diff** to ensure only wave-loot implementation, tests, config, spec, and plan files changed, with no generated artifacts staged or modified intentionally.
