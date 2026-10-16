# Full-line transfer pass reuse

## Problem

Bidirectional Expansion line transfers already reuse one validated target traversal between their push and grab
phases when `max-targets-per-tick` is a partial-line budget.

When the target budget is unlimited (`0`) or greater than/equal to the configured line distance, the code bypassed
that per-tick cache and called the full line walker independently for push and grab. The same contiguous
`StorageCacheUtils.getMenu(...)` lookup path could therefore run twice in one transfer tick.

The maintained defaults are still budgeted (8 targets for line transfer and 12 for line grabber), so this mainly
affects customized unlimited/full-line configurations.

## Change

- Full-line push passes can capture the validated `BlockMenu` targets on the current call stack.
- The immediately following grab phase replays those same targets rather than resolving the line again.
- No `BlockMenu` reference is stored in a field, static map, or across ticks.
- Full-line mode continues to ignore and clear cursor state exactly as before.
- Budgeted cursor behavior is unchanged.
- Transfer mode, target order, quantities, power cost, source guards, VOID semantics and item-routing behavior are unchanged.

## Validation

The source contract now requires both budgeted and full-line pass reuse while prohibiting a retained
`LINE_PASS_CACHE_MAP`. Existing unit, three-core compatibility, Java 21 universal-JAR, Paper 26.3 and native
runtime/restart gates remain authoritative. Performance should be validated with comparable server timing samples.
