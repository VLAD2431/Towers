# Wave Mob Damage Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Block damage to wave mobs when the resolved attacker is outside their 50-block horizontal rift boundary.

**Architecture:** Store rift centers as already done in `GameManager`; expose a boundary predicate for an entity's damage source and cancel `EntityDamageByEntityEvent` when it fails. Resolve projectiles to their shooter.

**Tech Stack:** Java 21, Paper API 1.21.7, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-11-16-wave-mob-damage-boundary-design.md`

## Global Constraints
- Preserve non-wave mob behavior and environmental damage.
- Reuse existing 50-block horizontal boundary semantics.
- Run `mvn test-compile` after Java changes.

## Review Focus
- Ranged projectile attacks must use shooter location, not projectile location: test outside and inside shooters.
- Ordinary mobs and environmental damage must remain unaffected: test both.
- Unknown rift centers must not inadvertently change existing damage behavior: test this case.

---

### Task 1: Enforce the damage boundary

**Files:**
- Modify: `src/main/java/VLADMIX90/towers/game/GameManager.java`
- Modify: `src/main/java/VLADMIX90/towers/listener/GameListener.java`
- Test: `src/test/java/VLADMIX90/towers/listener/GameListenerTest.java`

**Interfaces:**
- Consume existing `GameManager.WAVE_MOB_TAG`, `waveMobRiftCenters`, and `isWithinRiftBoundary(Location, Location)`.
- Produce a handler for `EntityDamageByEntityEvent` that cancels only wave-mob damage attributable to an out-of-bound attacker.

- [ ] Write tests covering direct attacker in/out of bounds, projectile shooter in/out of bounds, ordinary target, and environmental damage.
- [ ] Run `mvn -Dtest=GameListenerTest test` and confirm the new behavior tests fail for the expected missing cancellation.
- [ ] Implement a damage-boundary predicate in `GameManager`, resolve projectile shooters in `GameListener`, and cancel attacks only when a known wave-mob center exists and the attacker is outside it.
- [ ] Run `mvn test` and `mvn test-compile`; both must pass.
