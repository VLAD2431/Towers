# Weighted scroll drops

## Intent and approved behavior

Allow external QUACK scrolls to have a shared 50% drop chance. A successful group roll selects exactly one scroll using relative weights; a failed roll selects none. Preserve authentic item metadata and existing ordinary-loot behavior.

## Approaches

- Independent item rolls: existing behavior; can produce multiple scrolls, so rejected.
- Shared gate followed by independent rolls: can still produce zero or multiple scrolls after success, so rejected.
- Shared gate followed by one weighted selection: chosen and approved in conversation.

## Implementation decisions

- Add `rift-scrolls.chance`, default 50, configurable from 0 to 100, applied on configuration reload.
- All eligible entries with `skin-id` form one group, after existing wave-type loot filtering. Ordinary entries remain independent.
- Add optional nonnegative finite `weight` to scroll entries. For existing entries without `weight`, use their existing `chance` as the weight. Explicit weights are relative, not percentages, and may exceed 100.
- Zero/invalid weights are excluded. An empty or zero-total group yields no scroll and consumes no selection roll.
- Preserve ordinary `replaces-all` priority. If an ordinary exclusive item wins, skip the scroll group. A selected exclusive scroll suppresses other drops; unsuccessful/unselected scrolls suppress nothing.
- Grouped scrolls do not receive individual `chance` or `guaranteed-in-cycle` rolls: those would violate the shared probability and one-scroll limit. Warn for scroll cycle guarantees and do not assign them. Ordinary cycle guarantees remain unchanged.
- Keep existing missing-plugin/unknown-skin behavior: skip the unavailable item and log a warning, without rerolling or manufacturing a replacement. Thus 50% describes selection probability, not guaranteed delivery when QUACK is unavailable or misconfigured.
- Do not activate fake skin IDs in the bundled configuration; provide commented examples with weights 7, 2, 1.

## Scope and constraints

Small change to loot settings, selection, runtime call sites, and tests. Java 21, existing Maven/Paper stack, no new dependencies. No unrelated refactoring, no main-thread external I/O, and no changes to external scroll creation.

## Verification

Deterministic tests cover group success/failure, weighted boundaries, at most one scroll, invalid/zero weights, filtering, exclusivity, cycle rules, reload, and both runtime reward paths. Run the focused tests, full Maven tests, and `mvn compile` before completion.
