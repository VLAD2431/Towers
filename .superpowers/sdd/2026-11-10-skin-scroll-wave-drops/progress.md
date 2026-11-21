# SDD ledger — plan: docs/superpowers/plans/2026-11-10-skin-scroll-wave-drops.md

Baseline: QUACK 44 tests passed. Questline 218 tests, one existing failure: ShippedConfigParticleTest.everyWaveMobHasBothParticles (type-3/custom:pumpkin missing particles).
Task 2 parser: skin-id test observed RED, implemented and GREEN (14 tests).
Ruling: expose SkinsPlugin.createSkinScroll(String) and invoke through a narrow reflective Questline adapter rather than duplicate a ServicesManager interface in two plugin classloaders — avoids a shared runtime/API artifact and keeps QUACK optional — incompatible older QUACK builds are warned/skipped.
Ruling: work in the two existing project directories with user approval; Questline has no git repository — no automatic commits or workspace cleanup, preserve the implementation and ledger.
Pre-flight: API returns ItemStack/null; Questline must skip null in both world drops and test-loot inventory delivery.
Task 1: API tests observed RED (missing method), GREEN; QUACK full suite 46 tests, 0 failures. Added Mockito test-scoped only to test the real plugin method without running JavaPlugin lifecycle; no runtime dependency.
Task 2: builder/provider tests observed missing API RED then GREEN. Full Questline suite 227 tests: only known baseline particle failure remains. Delivery regression checks cover inventory/world paths with present/absent plugin (4 passing cases).
Task 2: material independence test observed RED (skin entry with invalid material discarded); fixed parser so skin-id entries use a PAPER placeholder and bypass ordinary material validation.
Task 1: complete — mvn package passed, 46 tests; target/QUACK-1.0.jar built. Mockito is test-only.
Task 2: complete — focused integration tests 29/29 passed; full suite 231/232 with baseline particle failure only. target/towers-1.0.jar built with -DskipTests after reporting the full suite failure.
Final review: independent general reviewer found no integration-specific issue.
Final: Ruling: reviewer identified finishWaves assumes its non-null Location has a World — leave this preexisting behavior unchanged because null-world support was not requested and production locations originate from live worlds — an independently corrupted/stale Rift location could still interrupt completion.
Final: removed unnecessary reflective setAccessible fallback; QUACK's public method on its public plugin class needs no access override.
Task 3: complete — full suites executed, artifact paths verified, skin-plugin git diff --check passed. No live Paper server available; actual in-game scroll redemption remains untested.
