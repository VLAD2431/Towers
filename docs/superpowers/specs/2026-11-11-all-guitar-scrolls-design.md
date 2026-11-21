# All guitar scrolls in wave loot

## Intent and scope
The user replaced the approved ten soul/impuls scrolls with every guitar scroll. Add all 12 existing QUACK guitar skin IDs to Questline's shipped loot configuration and every one of its four wave-type loot lists.

IDs: guitar_classic, guitar_blue, guitar_wave, guitar_litghtningbolt, guitar_black, guitar_gradient, guitar_mgla, guitar_white, guitar_badtime, guitar_glem, guitar_gold, guitar_soul. Preserve the actual QUACK spelling guitar_litghtningbolt.

## Approach
Use existing skin-id integration and config-only loot entries, each with weight 1 and a distinct loot ID prefixed scroll_. Keep rift-scrolls.chance at 50: one scroll per successful group roll, equally weighted. Existing ordinary exclusive loot retains priority.

## Alternatives
Configuration-only integration reuses authentic QUACK item creation and is selected. Java changes duplicate working functionality and are unnecessary. The earlier ten soul/impuls selection is superseded by the user's latest instruction.

## Constraints and non-goals
Keep all ordinary loot definitions and existing wave loot members unchanged, including unrelated pre-existing configuration defects. Do not alter QUACK or Java runtime code. Preserve UTF-8 text and unrelated formatting. This updates the source default configuration, not a live server configuration.

## Verification
Extend ShippedConfigLootTest to assert 16 entries: the existing four ordinary entries followed by all 12 guitar scrolls, unique IDs, weight 1, non-exclusive behavior, shared chance 50, and membership of all guitar scrolls in each of the four wave types. Run the focused tests and mvn compile. No Git commit is possible because this directory is not a Git repository.
