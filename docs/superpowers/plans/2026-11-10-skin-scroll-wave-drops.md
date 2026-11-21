# Skin Scroll Wave Drops Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow Questline wave loot to drop authentic skin-unlock scrolls created by skin-plugin (QUACK), configured by skin ID.

**Architecture:** QUACK exposes public `SkinsPlugin.createSkinScroll(String): ItemStack`. Questline optionally discovers the enabled plugin and invokes this narrow API reflectively, avoiding duplicated service-interface classes or a hard runtime dependency. Only `skin-id` loot uses it; ordinary loot remains unchanged. Missing/old QUACK or an unknown ID warns and skips the item. Scroll entries produce one authentic, non-stackable token; ordinary loot keeps configured quantities.

**Implementation ruling:** The public method replaces the ServicesManager interface described in the original task breakdown below. No service registration/unregistration or shared API JAR is needed; the adapter checks plugin enabled state on every call. Both world drops and test-loot delivery must skip missing items. Add focused adapter tests for missing/disabled/old plugins, null results, exceptions, and unchanged returned item identity.

**Tech Stack:** Java 21, Paper API 1.21.7, Maven, JUnit 5.

**Spec:** Approved in conversation on 2026-11-10: API integration; preserve ordinary loot; both plugins installed for skin-scroll entries.

## Global Constraints

- Both projects use Java 21 and Paper API 1.21.7.
- Questline must remain usable without QUACK installed.
- Scrolls must be created by QUACK's existing `SkinManager.createSkinToken` implementation to preserve their metadata and appearance.
- Existing loot chance, quantity, cycle-guarantee, exclusive-drop and wave-type selection behavior must remain intact.
- Compile/test with `mvn test-compile` or `mvn test` in each affected project.

## Review Focus

- QUACK absent: ordinary Questline loot still works and configured skin-scroll entries are warned/skipped.
- Unknown configured skin ID: no invalid item is produced; log identifies the ID.
- Skin-scroll loot retains the full authentic token metadata and amount is one (scrolls are non-stackable).
- Existing non-skin entries retain their current parsing and drop behavior.
- The bridge checks plugin enabled state at delivery time; it holds no stale service reference.

---

### Task 1: Expose skin-scroll creation API in QUACK

**Files:**
- Modify: `skin-plugin/src/main/java/VLADMIX90/skinPlugin/SkinsPlugin.java`
- Test: `skin-plugin/src/test/java/VLADMIX90/skinPlugin/SkinScrollApiTest.java`
- Modify: `skin-plugin/pom.xml` (test-only Mockito)

**Interfaces:**
- Produces: public `SkinsPlugin#createSkinScroll(String skinId): ItemStack`, returning `null` for an unknown ID.

- [x] **Step 1: Write a failing API behavior test** asserting that a known configured skin creates a token using the existing token factory and an unknown ID returns `null`.
- [x] **Step 2: Run the focused test and verify it fails** because the public API is not present.
- [x] **Step 3: Implement the public QUACK API** delegating directly to `ConfigManager#getSkin` and `SkinManager#createSkinToken`.
- [x] **Step 4: Run `mvn test` in `skin-plugin`** — 46 tests pass.

### Task 2: Parse and materialize optional skin-scroll loot in Questline

**Files:**
- Modify: `Questline/src/main/java/VLADMIX90/towers/wave/RiftLootSpec.java`
- Modify: `Questline/src/main/java/VLADMIX90/towers/wave/WaveConfig.java`
- Modify: `Questline/src/main/java/VLADMIX90/towers/wave/RiftLoot.java`
- Modify: `Questline/src/main/java/VLADMIX90/towers/game/GameManager.java`
- Create: `Questline/src/main/java/VLADMIX90/towers/wave/SkinScrollProvider.java`
- Modify: `Questline/src/main/resources/plugin.yml`
- Test: `Questline/src/test/java/VLADMIX90/towers/wave/RiftLootTest.java`
- Test: `Questline/src/test/java/VLADMIX90/towers/wave/SkinScrollProviderTest.java`
- Test: `Questline/src/test/java/VLADMIX90/towers/game/GameManagerSkinLootTest.java`

**Interfaces:**
- Consumes: `SkinsPlugin#createSkinScroll(String skinId): ItemStack` through the enabled plugin's public method.
- Produces: optional `skinId`; loot builder receives `Function<String, ItemStack>` and preserves the API-created item, setting quantity to one.

- [x] **Step 1: Add failing parser/build tests** for optional `skin-id`, authentic item use, and ordinary loot remaining unchanged.
- [x] **Step 2: Run focused tests and verify the new assertions fail.**
- [x] **Step 3: Extend the loot spec/parser/build path** so skin entries bypass material-built items; missing API/items warn and skip.
- [x] **Step 4: Wire the optional API lookup and declare `softdepend: [CustomMob, QUACK-by-VLADMIX90]`.**
- [x] **Step 5: Document a commented `rift-loot` example** without enabling an arbitrary skin ID.
- [x] **Step 6: Run full Questline suite** — 231/232 pass; only preexisting `ShippedConfigParticleTest.everyWaveMobHasBothParticles` fails. Focused integration tests pass.

### Task 3: Verify both projects together

**Files:**
- No additional files expected.

- [x] **Step 1: Run full QUACK suite and package** — 46 tests pass; `target/QUACK-1.0.jar` built.
- [x] **Step 2: Run full Questline suite** — known baseline failure only; package separately with `-DskipTests`.
- [x] **Step 3: Independent review and artifact checks** — review found only a preexisting null-world assumption outside this change. No remote actions or commits.
