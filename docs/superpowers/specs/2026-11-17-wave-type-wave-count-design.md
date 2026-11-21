# Wave-Type Wave Count Design

## Intent

Allow a wave type to specify how many waves a run of that type contains. This lets `harry_mono` run for one wave while preserving the global default for other types.

## Behavior

- Add an optional `waves` value to each `wave-types.<id>` entry.
- If present and valid, that value is the total number of waves for a run using that type.
- If absent, use the existing `chunk-area.waves` value.
- Keep `mobs.<mob>.counts` as per-wave mob quantities; it does not determine total wave count.
- Set `wave-types.harry_mono.waves: 1` in the shipped config.

## Approaches

- Per-type `waves` setting (chosen): explicit, localized, and keeps mob counts independent from run length.
- Infer run length from `counts`: rejected because it conflates mob quantity data with wave-count policy.

## Constraints and Non-Goals

- Existing wave types and configs without `waves` retain current behavior.
- Do not change mob spawning, wave intervals, or loot behavior.
- No schema migration is needed; configuration remains static YAML.

## Verification

Test that a type-specific count overrides the global setting, missing values fall back to the global setting, and `harry_mono` is configured for one wave. Run the relevant tests and `mvn test-compile`.
