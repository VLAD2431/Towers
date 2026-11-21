# SDD ledger — plan: C:\Users\VLADMIX90\.opencode\plan\2026-10-22-rift-cloud-and-sounds.md

Setup ruling: the project is not a git repository, so the skill's git/worktree/task scripts cannot run. The plan file is the brief; this ledger is maintained by hand. Verification is `mvn -o test` / `mvn -o clean package` with `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`, Maven `C:\Program Files\apache-maven-3.9.16\bin\mvn.cmd`. Cost if wrong: no commit history — recoverable via diffs in the conversation.

TDD ruling: tests are written first and watched fail for each behavior. Config.yml is covered by shipped-config tests. The runtime-only sound playback is proven by Mockito interactions on the scheduler/player; actual audibility remains an in-game checklist item.

Cross-task interfaces:
- Task 1 produces `RiftParticleSpec` and the new `RiftCloud(particles, interval, viewDistance)`; Task 2 consumes them in `Rift`.
- Task 3 removes sound keys/calls; Task 4 adds `sounds.rift-close` and `sounds.battle` plus `GameManagerBattleSoundTest`, and extends the shipped sound list.
- Task 4's battle loop uses `ConfigManager.integer("sounds.battle-interval-seconds", 8)` and must not overlap with Task 3's `advanceAfterWave` edits.

## Task 1 � cloud model + parsing � done
- Added `RiftParticleSpec` and changed `RiftCloud` to `(particles, interval, viewDistance)`.
- `WaveConfig.cloud` parses `cloud.particles` and falls back to legacy `cloud.type/count/radius`.
- Shipped config now has OMINOUS_SPAWNING (10, 2.5/1.5/2.5, 0.2) and DUST_COLOR_TRANSITION (5, 2.5/0.5/2.5, 0, #E6FFFF>#2BA3D9, size 3).
- Verified: 17 tests green (WaveConfigRiftTest, ShippedConfigRiftCloudTest).

## Task 2 � cloud lifetime � done
- `Rift.tick` calls `showCloud` for every non-CLOSED phase, including OPEN/PULSE/CLOSE.
- `showCloud` emits every `RiftParticleSpec` and uses the data overload for dust transitions.
- Verified: 26 tests green (RiftTest, GameManagerWaveStartTest); the opened-rift test was watched fail first.

## Task 3 — removed quest-given, quest-selected, wave-clear — done
- Removed the three calls, the now-unused ConfigManager from MenuClickListener, and the config keys.
- ShippedConfigSoundsTest now checks these keys are absent; ConfigManagerSoundTest uses quest-complete.
- Verified: 9 tests green (ShippedConfigSoundsTest, ConfigManagerSoundTest); grep found no `sounds.<removed>` references.

## Task 4 — rift-close + battle loop — done
- Added sounds.rift-close, sounds.battle, sounds.battle-interval-seconds (8).
- GameManager starts one battle loop when a wave spawns, resumes it after relog, stops it on wave clear/quit/cancel/cleanup.
- Plays sounds.rift-close on normal finish and cancel/reset cleanup, not on quit/cleanup.
- Verified: 15 tests green (GameManagerBattleSoundTest, GameManagerWaveStartTest, ShippedConfigSoundsTest).

## Task 5 — README + final build — done
- README now documents the new cloud format, cloud lifetime, five custom sounds, battle interval, and rift-close behavior.
- `/questline test rift remove` also plays sounds.rift-close for the command player.
- Verified: `mvn -o clean package` → BUILD SUCCESS, 191 tests, 0 failures. Jar: target/Questline-1.0-SNAPSHOT.jar (shaded).
- Review follow-up: added regression tests for cloud emission during CLOSE and none after the close animation, wave-clear/stop, reset/cleanup stop, resume-after-relog; refreshed stale rift Javadoc, sound example, and close-sound docs.
- Not verified: in-game audibility and visual checks (need a running Paper server and the real resource-pack sound ids).

