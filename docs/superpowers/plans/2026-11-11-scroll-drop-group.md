# Weighted Scroll Drops Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Completed steps are checked.

**Goal:** Select at most one authentic scroll with a configurable shared chance defaulting to 50% and per-scroll relative weights.

**Architecture:** WaveConfig loads the shared probability and per-entry weights. RiftLoot performs one shared gate and one weighted choice over scrolls in the already-filtered table, retaining ordinary-loot rules. Both GameManager reward paths supply the configured probability.

**Tech Stack:** Java 21, Paper API 1.21.7, Maven, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-11-11-scroll-drop-group-design.md`

## Global Constraints

- Shared group chance defaults to 50%; a successful group selects exactly one positive-weight scroll.
- Use existing QUACK scroll creation, preserving metadata; missing dependencies/items are skipped without rerolling.
- Ordinary loot probabilities, cycle guarantees, and exclusive-loot priority remain unchanged.
- Preserve wave-type filtering and reload behavior; no new dependencies or unrelated changes.
- Compile Java changes with `mvn compile`; no Git commits because this workspace is not a Git repository.

## Review Focus

- Fractional, negative, NaN, infinite, and very large weights must not produce invalid probabilities; normalize finite positive weights by the maximum before summation to avoid overflow.
- A group with no positive weights returns no scroll even at 100% shared chance.
- Scroll cycle guarantees must not bypass the shared chance or produce extra scrolls.
- Exclusive items retain suppression behavior without separately rolling each scroll.
- Both production rewards and test rewards must use the configured group chance and preserve unavailable-provider handling.

---

### Task 1: Configuration and weighted group selection

**Files:**
- Modify: `src/main/java/VLADMIX90/towers/wave/RiftLootSpec.java`
- Modify: `src/main/java/VLADMIX90/towers/wave/WaveConfig.java`
- Modify: `src/main/java/VLADMIX90/towers/wave/RiftLoot.java`
- Test: `src/test/java/VLADMIX90/towers/wave/RiftLootTest.java`

**Interfaces:**
- Consumes: existing `skinId()`, `chance()`, `replacesAll()`, `lootForType(String)` and `RiftLoot.build(...)`.
- Produces: `double RiftLootSpec.weight()` and `double WaveConfig.scrollChance()`.
- Produces: `List<LootDrop> RiftLoot.rollAll(List<RiftLootSpec> specs, Map<String, String> guaranteedDrops, String questId, Random random, double scrollChance)`.
- Retain the four-argument `rollAll` overload delegating with default 50%, and preserve existing RiftLootSpec constructor source compatibility if callers use it.

- [x] Add deterministic tests: gate values 0.49/0.50 at 50% respectively select one/no scroll; shared chance 0/100; weights 7/2/1 select the correct entry across cumulative boundaries; selected amount is one even with min/max overrides.
- [x] Add tests for fractional and >100 weights, absent-weight fallback to chance, zero/negative/nonfinite weights, empty groups, and finite weights whose raw sum overflows.
- [x] Add tests for ordinary loot alongside one scroll, ordinary exclusive priority, selected exclusive scroll suppression, and grouped scroll cycle flags not bypassing the gate or receiving assignments.
- [x] Add configuration tests for default/custom/clamped/nonfinite shared chance, reload, and wave-type filtering before selection.
- [x] Run `mvn -Dtest=RiftLootTest test`; observed behavioral failures before grouped selection/configuration existed.
- [x] Implement the interfaces and weighted selection. Check ordinary exclusives first, select the scroll once, return it alone if exclusive, otherwise independently roll ordinary items and append the selected scroll. Normalize positive finite weights by their maximum. Do not individually roll scroll chances or amounts.
- [x] Run `mvn -Dtest=RiftLootTest test` and `mvn compile`; 31 tests passed and compilation succeeded.

### Task 2: Runtime wiring and configuration instructions

**Files:**
- Modify: `src/main/java/VLADMIX90/towers/game/GameManager.java`
- Modify: `src/main/resources/config.yml`
- Test: `src/test/java/VLADMIX90/towers/game/GameManagerSkinLootTest.java`

**Interfaces:**
- Consumes: Task 1's five-argument `RiftLoot.rollAll(...)`, `WaveConfig.scrollChance()`, and existing filtered loot tables.
- Produces: identical group semantics in `finishWaves(UUID)` and `giveTestLoot(Player)`.

- [x] Set existing provider-success fixtures to explicit group chance 100 to avoid random tests; add 0% no-provider-call/no-delivery tests for both reward paths, with two configured scrolls to detect accidental legacy individual rolls.
- [x] Run `mvn -Dtest=GameManagerSkinLootTest test`; observed the two expected failures before runtime wiring.
- [x] Pass `waves.scrollChance()` at both runtime rollAll calls, preserving all existing creation/delivery code.
- [x] Add `rift-scrolls.chance: 50` and commented three-scroll examples using weights 7/2/1. Explain relative weights, shared chance, wave-type inclusion, ordinary exclusivity, and unsupported scroll cycle guarantees. Leave actual skin IDs for the administrator.
- [x] Run focused tests: 45 passed; full `mvn test`: 248 tests, one failure in `ShippedConfigParticleTest.everyWaveMobHasBothParticles` for `type-3/custom:pumpkin` lacking particles in the untouched wave-types section; standalone `mvn compile` succeeded. Do not change unrelated particle configuration.
- [x] Review changed files for unrelated edits, constructor compatibility, untested reward paths, and configuration examples matching parser keys; fresh review found no Critical, Important, or Minor issues.
