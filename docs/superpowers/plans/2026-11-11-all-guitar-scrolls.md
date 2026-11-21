# All Guitar Scrolls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this single task. Track execution with the checkboxes below.

**Goal:** Add every existing QUACK guitar scroll to all four wave types.

**Architecture:** Reuse the existing skin-id loot entries and grouped scroll selection. Only the shipped configuration and its regression tests change; runtime Java logic remains untouched.

**Tech Stack:** YAML, Java 21, Paper, Maven, JUnit 5.

**Spec:** docs/superpowers/specs/2026-11-11-all-guitar-scrolls-design.md

## Global Constraints
- Use all 12 exact guitar IDs in the spec, including guitar_litghtningbolt.
- Equal weight 1; shared scroll chance 50; no individual guarantees or exclusivity.
- Preserve all ordinary loot and existing wave loot members.
- No changes to QUACK, dependencies, or runtime Java code.
- No Git operations: working directory is not a Git repository.

## Review Focus
- All 12 entries must resolve to actual QUACK skin IDs, not guessed corrected spellings.
- No wave type may omit a guitar scroll, including type-3 with an initially empty loot list.
- Equal weights and shared chance must remain explicit and unchanged.
- Existing ordinary loot assertions must still pass unchanged except for the total entry count.
- Unique scroll-prefixed loot IDs must not collide with ordinary loot IDs.

### Task 1: Configure all guitar scroll drops and pin shipped defaults

**Files:**
- Modify: src/main/resources/config.yml
- Modify: src/test/java/VLADMIX90/towers/wave/ShippedConfigLootTest.java

**Interfaces:** Consume existing WaveConfig(YamlConfiguration), loot(), scrollChance(), RiftLootSpec.id(), skinId(), weight(), replacesAll(), guaranteedInCycle(). Produce configuration entries scroll_<skin-id> linked to each exact guitar skin-id and referenced from wave-types.type-1 through type-4 loot lists.

- [x] Update shipsTheExpectedLootTable's count to 16; add a regression test asserting the exact 12 guitar skin IDs, scroll-prefixed unique IDs, weight 1, no replaces-all or guaranteed-in-cycle, shared chance 50, and all 12 scroll loot IDs in each of the four wave loot lists. Retain existing ordinary loot assertions.
- [x] Run mvn "-Dtest=ShippedConfigLootTest" test; confirm failure is caused by missing guitar entries.
- [x] Append the 12 guitar entries after existing ordinary loot and add their loot IDs to each wave's loot list. Preserve type-3's formerly implicit ordinary loot explicitly when replacing its empty list. Leave all existing members and unrelated configuration text intact.
- [x] Run mvn "-Dtest=ShippedConfigLootTest,RiftLootTest,WaveConfigLootTypeTest,SkinScrollProviderTest" test; 41 tests, zero failures/errors.
- [x] Run mvn compile; BUILD SUCCESS. Full mvn test also passed: 249 tests, zero failures/errors. Independent read-only review found no issues. No commit because this is not a Git repository.
