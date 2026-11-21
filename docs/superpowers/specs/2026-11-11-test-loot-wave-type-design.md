# Test Loot Wave Type Design

## Intent
Allow admins to test the loot table for a chosen wave type without requiring a selected quest.

## Goals and Constraints
- Add `/towers test loot <type-id>` using IDs configured under `wave-types`.
- Keep `/towers test loot` behavior unchanged: it uses the selected quest and fails with the existing message if none is selected.
- Suggest configured wave type IDs through tab completion.
- Unknown IDs must not silently roll another type's loot.
- Reuse existing `WaveConfig` and `GameManager` loot rolling behavior; do not change live-wave rewards.

## Approaches
- Add an optional type ID to the existing test-loot command and pass it to a new `GameManager` overload (chosen: minimal and preserves current command behavior).
- Require a type ID for all test-loot invocations (rejected: breaks existing selected-quest workflow).
- Create a temporary quest to reuse the current method (rejected: introduces unrelated quest state).

## Design
Add an overload that rolls the explicitly selected type's table directly, while retaining `giveTestLoot(Player)` for the selected-quest path. The command validates the requested ID against `WaveConfig.typeIds()` through `GameManager`, reports success or an unknown type, and tab-completes configured IDs. Tests cover explicit selection, unknown IDs, and unchanged no-argument behavior.

## Non-goals
No changes to wave execution, reward selection during actual gameplay, or loot configuration format.
