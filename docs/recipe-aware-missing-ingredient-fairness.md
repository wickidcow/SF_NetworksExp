# Experimental Recipe-Aware missing-ingredient fairness

Opt-in Recipe-Aware Network Pushers with multiple ingredient templates may feed
shared input slots unevenly. For example, an eight-batch recipe with 9 iron and
1 gold requires 72 iron; the iron can fill two shared slots before any gold enters.

This experimental patch caps any ingredient to one batch while ANOTHER required
ingredient is completely absent from the target's menu. Once all ingredient
types have at least one stack present, normal 1–64 buffered targets resume.

The scan runs once per pusher tick only for multi-template Recipe-Aware mode.
It does not expand or override machine-specific safe transport slots.
Non-Recipe-Aware routing, block data, item IDs and templates remain unchanged.

The presence heuristic also scans menu slots that may be display-only; it cannot
guarantee completion of recipes with incompatible/dedicated input layouts.
An end-to-end busy machine test and tick-performance comparison remain required.

Do not merge with the already validated 1.0.59-rc1 staging candidate until the
three-core Java 21 and native Paper/Purpur tests pass for this change.
