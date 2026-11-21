# Wave-Type Wave Count Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow each wave type to override the global run length, setting `harry_mono` to one wave.

**Architecture:** Parse optional `waves` from each wave type and use it to initialize a run's remaining-wave count; absent values retain the configured global default. Keep mob `counts` independent from total run length.

**Tech Stack:** Java 21, Paper API, Maven, JUnit tests.

**Spec:** `docs/superpowers/specs/2026-11-17-wave-type-wave-count-design.md`

## Global Constraints

- Wave types without `waves` retain `chunk-area.waves` behavior.
- Mob `counts` remain per-wave mob quantities.
- Configuration remains static YAML; no data migration.
- Do not change mob spawning, intervals, or loot.

## Review Focus

- Missing per-type `waves` falls back to global setting: add fallback assertion to the configuration test.
- A type override controls run initialization rather than only mob count lookup: assert selected type initializes the correct run length.
- `harry_mono`'s shipped setting remains one: assert it in shipped-config coverage.
- Invalid or nonpositive overrides should not create a zero-wave run: test and clamp/fallback consistently with existing config conventions.
- Existing configurations remain compatible: test parsing a type without the new key.

---

### Task 1: Parse and apply per-type wave count

**Files:**
- Modify: `src/main/java/VLADMIX90/towers/wave/WaveTypeSpec.java` (inspect actual record shape; add `waves` override with global-fallback semantics)
- Modify: `src/main/java/VLADMIX90/towers/wave/WaveConfig.java`
- Modify: `src/main/java/VLADMIX90/towers/game/GameManager.java`
- Modify: `src/main/resources/config.yml`
- Test: `src/test/java/VLADMIX90/towers/wave/WaveConfigWaveCountTest.java` (create if no suitable existing test)

**Interfaces:**
- `WaveConfig` exposes the resolved run length for a type, falling back to `waves()` when the type has no override.
- `GameManager` uses that resolved count when starting new runs; resume behavior continues to use persisted `remainingWaves`.

- [x] **Step 1: Write failing configuration tests** for a type override of `1`, a type without override falling back to global `3`, and a nonpositive override not producing a zero-wave run.
- [x] **Step 2: Run the focused test** with `mvn -Dtest=WaveConfigWaveCountTest test`; confirmed compilation fails on missing `wavesForType` API.
- [x] **Step 3: Implement type-level parsing and resolved wave-count lookup**, following existing config parsing/default conventions.
- [x] **Step 4: Set `wave-types.harry_mono.waves: 1`** in the shipped `config.yml`.
- [x] **Step 5: Use the selected wave type's resolved count when initializing a new run**, without changing resumed runs.
- [x] **Step 6: Run focused tests and `mvn test-compile`**; both pass. Focused tests: 11/11 pass.

Full-suite note: `mvn test` reports 315 passes and 4 unrelated failures/errors: standalone-wave Mockito setup expected old API behavior (fixed by stubbing new API; focused suite now passes), two existing `ShippedConfigLootTest` expectations expect 18 loot entries while the current shipped config has 20, and test ordering causes Bukkit `Registry` initialization failure in the shipped-config test. Do not alter unrelated loot expectations as part of this feature.
