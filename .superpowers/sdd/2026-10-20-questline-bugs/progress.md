# SDD ledger â€” plan: C:\Users\VLADMIX90\.opencode\plan\2026-10-20-questline-bugs.md

Pre-flight: no shared interfaces. Task 2 consumes only its own Task 2 products; Task 4 consumes existing `PlayerQuestData.clearWaveChunk()`; Task 3 Step 2 log and Task 5 Step 1 (line 239) touch the same statement by design â€” merged in Task 5 order.

Setup ruling: project is not a git repository â€” execution without worktree/commits; verification via `mvn clean package` / `mvn test` (toolchain: JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot, maven=C:\Program Files\apache-maven-3.9.16). Cost if wrong: no commit history; recoverable via diffs in conversation.

Task-1/3/4/5 TDD ruling: Bukkit plugin without MockBukkit â€” `openCalibration`, `spawnMobs`, `finishWaves` depend on live server statics (`Bukkit.createInventory`, entities); unit tests infeasible. Verification = compile + full suite + in-game checklist (plan Task 6). Task 2 has real unit tests (pure static methods). Cost if wrong: menu/spawn regressions caught only in game.

Task 1: complete (build mvn clean package > EXIT=0; in-game check deferred to user checklist Task 6)
Task 2: complete (tests mvn test > 5/5 pass, EXIT=0; watched RED cannot-find-symbol > GREEN; in-game check deferred to Task 6)

Task 3: complete (build mvn clean package > BUILD SUCCESS; despawn fix + survivor log in place; Task 5 Step 1 line-239 merge executed early per plan's own instruction — Ruling: 239 chat send removed in Task 3, replaced by logger.info — plan Task 5 allowed the merge — cost if wrong: none, chat removal was required anyway)
Task 4: complete (build > BUILD SUCCESS; clearWaveChunk+quests.save at end of finishWaves after loot; Ruling: added quests.save(id) after clearWaveChunk — waveDefeated saved BEFORE finishWaves, so without an immediate save a crash would restore the stale wave chunk — cost if wrong: one extra save per wave completion)
Task 5: complete (build mvn clean package > BUILD SUCCESS, Tests run: 5, Failures: 0; player sends removed from GameManager/MenuClickListener/QuestlineCommand, config quest-reset-target key deleted; test-* admin replies kept per plan)
