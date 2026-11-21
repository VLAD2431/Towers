# Towers Rename Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Rename the complete Minecraft plugin identity from Questline to Towers.

**Architecture:** Keep the existing plugin architecture and behavior. Rename package/class paths, plugin metadata and command contracts, namespaces/persistence, and project documentation/build metadata in place.

**Tech Stack:** Java 21, Paper API 1.21.7, Maven, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-11-10-towers-rename-design.md`

## Global Constraints
- Java 21 and Paper API 1.21.7 remain unchanged.
- Full identity change; do not retain old names for compatibility or migrate old persisted data/PDC tags.
- Never add `target/` or JAR files to source changes.

## Review Focus
- Plugin metadata points to a class that does not exist: validate main class and registered command against the renamed source.
- A remaining old namespace prevents newly tagged entities or custom sounds from matching: search active source/resources/docs for old identifiers.
- Maven shade output retains the old artifact identity: verify the resulting artifact filename.
- Java source path and package declarations diverge: compile all production and test sources after moving both trees.

---

### Task 1: Rename plugin code and runtime identity

**Files:**
- Move all `src/main/java/VLADMIX90/questline/**` to `src/main/java/VLADMIX90/towers/**`.
- Move all `src/test/java/VLADMIX90/questline/**` to `src/test/java/VLADMIX90/towers/**`.
- Rename main class `Questline.java` to `Towers.java`; rename command classes to `TowersCommand` and `TowersTabCompleter`.
- Modify all Java declarations/references, plugin.yml, config.yml, sound tests, PDC namespace, and persistence naming.

**Interfaces:**
- Consumes: current plugin classes and package `VLADMIX90.questline`.
- Produces: `VLADMIX90.towers.Towers`; command `/towers`; permission `towers.admin`; PDC and resource-pack namespace `towers`.

- [ ] Update tests/config expectations to the new package and names, including custom sound names and persisted path expectations.
- [ ] Run tests to identify remaining stale compile/runtime expectations.
- [ ] Rename Java source tree/package/class and internal runtime names; preserve behavior.
- [ ] Run `mvn test-compile`.
- [ ] Run `mvn test`.

### Task 2: Rename project and documentation metadata

**Files:**
- Modify `pom.xml`, `README.md`, `Questline.iml`, `.idea/modules.xml`, `.idea/compiler.xml`, `.idea/workspace.xml` where relevant.

**Interfaces:**
- Consumes: Task 1 runtime identity `towers`.
- Produces: artifact `Towers-1.0.jar` and project-facing instructions using `/towers` and `plugins/Towers/`.

- [ ] Update Maven artifact/name and active IDE module references.
- [ ] Update README commands, plugin path, sound IDs, and JAR filename.
- [ ] Search active project inputs for stale Questline/questline identity, excluding historical notes, `target/`, and temporary logs.
- [ ] Run `mvn clean package` and verify BUILD SUCCESS, tests pass, and `target/Towers-1.0.jar` exists.
