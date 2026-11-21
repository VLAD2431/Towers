# Wave-Specific Loot Design

## Intent
Allow server owners to define reusable loot entries by ID and choose which entries can drop for each wave.

## Goals
- Define each item once in a loot catalog and refer to it by ID in wave settings.
- Roll and award the configured loot once, when the final wave ends and the rift closes.
- Preserve existing `rift-loot` behavior when the new catalog/configuration is absent.

## Non-goals
- Changing loot item construction, chance/amount semantics, or guaranteed-in-cycle behavior beyond associating entries with a wave.
- Adding commands or an in-game loot editor.

## Approaches
- **Catalog plus wave ID lists (selected):** Reuse item definitions and list IDs under each wave's parameters. Directly matches the requested workflow and avoids duplicated item definitions.
- **Wave numbers on each loot entry:** Requires fewer wave-side edits but puts wave assignment on the item, contrary to the requested configuration workflow.

## Behavior
The selected wave configuration determines which catalog entries are eligible for the final loot roll. Loot is rolled and dropped once, when the final wave ends and the rift closes; it is not dropped after intermediate waves. Existing `rift-loot` remains the fallback when the new format is not configured. Invalid item IDs should be ignored with a configuration warning.

## Implementation choices
Follow the current Paper/Bukkit configuration and loot-building patterns. Add focused tests for parsing, ID resolution, and per-wave selection, then run `mvn test-compile` as required by the repository instructions.

## Open for implementation
Choose the exact YAML key names in line with the existing config style; no user-facing behavior beyond the above is left undecided.
