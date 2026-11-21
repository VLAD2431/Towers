# Test Loot Wave Type Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let `/towers test loot <type-id>` roll a configured wave type's loot table without requiring a selected quest.

**Architecture:** Add an explicit-type `GameManager` method alongside the existing quest-based method. Route the optional command argument through it and use `GameManager` to expose configured IDs to tab completion.

**Tech Stack:** Java 21, Paper API, Maven, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-11-11-test-loot-wave-type-design.md`

## Global Constraints
- Use Paper API 1.21+ and Java 21.
- Preserve existing behavior of `/towers test loot` without an argument.
- Do not add legacy `ChatColor` formatting.
- Run `mvn test-compile` after Java changes.

## Review Focus
- Unknown type ID: reject without granting loot or falling back to another table.
- Empty configured type list: tab completion returns no type suggestions.
- No-argument invocation: still requires and uses the selected quest as before.
- Explicit ID invocation: works when no quest is selected.
- Case handling: command type IDs follow the existing case-sensitive config IDs; completion filters by typed prefix.

---

### Task 1: Explicit wave-type loot test path

**Files:**
- Modify: `src/main/java/VLADMIX90/towers/game/GameManager.java`
- Test: `src/test/java/VLADMIX90/towers/game/GameManagerTestLootTest.java`

**Interfaces:**
- Produces: `public boolean giveTestLoot(Player player, String waveTypeId)`; returns false for an unconfigured ID, otherwise rolls that type's loot table and returns true.
- Produces: `public List<String> waveTypeIds()` exposing configured IDs for completion.

- [ ] **Step 1: Write failing tests**
  - Verify explicit type ID rolls `waves.lootForType(typeId)` and grants the resulting item without a selected quest.
  - Verify an unknown type returns false and does not access the player's inventory.
- [ ] **Step 2: Run `mvn -Dtest=GameManagerTestLootTest test`**
  - Expected: test compilation or assertions fail because the overload/accessor do not exist.
- [ ] **Step 3: Implement the overload and ID accessor**
  - Validate membership in `waves.typeIds()` before rolling. Reuse existing `RiftLoot.rollAll`, `buildLoot`, and inventory delivery.
- [ ] **Step 4: Run `mvn -Dtest=GameManagerTestLootTest test`**
  - Expected: all tests in the class pass, including existing selected-quest tests.

### Task 2: Command routing and completion

**Files:**
- Modify: `src/main/java/VLADMIX90/towers/command/TowersCommand.java`
- Modify: `src/main/java/VLADMIX90/towers/command/TowersTabCompleter.java`
- Test: add or update focused command/tab-completion tests if existing command test infrastructure supports it.

**Interfaces:**
- Consumes: `GameManager.giveTestLoot(Player, String)` and `GameManager.waveTypeIds()` from Task 1.

- [ ] **Step 1: Add command behavior checks**
  - Check `/towers test loot <type-id>` invokes explicit type selection and reports success or invalid type.
  - Check `/towers test loot` continues through the no-argument selected-quest path.
- [ ] **Step 2: Run focused command tests and verify failure**
- [ ] **Step 3: Route optional third argument and provide ID suggestions at argument four**
  - Tab complete only for `test loot` at `args.length == 4`; use configured IDs and the existing prefix matcher.
- [ ] **Step 4: Run `mvn test-compile` and `mvn test`**
  - Expected: compilation succeeds and the complete suite passes.
