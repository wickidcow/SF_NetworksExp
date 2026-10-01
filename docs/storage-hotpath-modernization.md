# Networks: optional IE2 storage lookup modernization

## Scope

This batch is based on `d9ffb4467c8bed3b137894fe6f2862838ea36cea` (1.0.45). It changes the optional IE2 position-accessor lookup and transport-slot filtering only. The cache traversal order, direct-key constructor preference, persisted fallback, live inventory reads, clone behavior, transfers, priorities, recipes, crafting rates, storage amounts and IDs are unchanged. No data writer, configuration, dependency, resource-pack mapping or public API descriptor changes.

## Reduced repeated work

The relocated/unofficial IE2 position-key fallback previously performed `Class.getMethod` for each attempted accessor on each key visit, including repeated missing-method exceptions. `PositionAccessorCache` caches only successful method discovery and stable `NoSuchMethodException` absence per exact runtime class/name. It uses ClassValue rather than a global strong Class-keyed registry. Every accessor still invokes on the current supplied key; no coordinates, world values, item data or inventories are cached.

Security and linkage failures during discovery are retried. Invocation failures and null return values are not treated as permanent method absence. Public/inherited no-argument resolution and reflection accessibility remain unchanged; there is no setAccessible bypass. The existing eager fallback accessor order is retained.

Slot normalization now uses a bounded 54-bit membership mask instead of stream-based distinct processing. It preserves first-occurrence order, the original legal range 0 through 53, and detached output arrays. It re-reads changing addon layouts rather than caching mutable slot-array contents.

## Actual validation against the new core

Core run [36779495717](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36779495717) built the exact five Java source files against the hash-verified Slimefun Legacy Doctor/storage candidate, PR head `788b89e17d4e8694bad2c4e0e5e9ac5b1e0662c6`, tested merge `d600e077552baab2086a056d04b1cb0a6a9051a5`. Core JAR SHA-256: `48a4871ec83f7d7cdd0af5696ce1efb46c6437fa726575dc6805dc752c239778`.

All **47 JUnit tests passed**, no failures/errors/skips, including **13 new accessor cases and 7 new slot-layout cases**. The full existing Networks source verifier, strict Java deprecation/removal compilation, Java 21 bytecode verifier and universal-JAR packaging verifier passed. A separate generic Gradle build-feature deprecation notice remains; zero explicit Java compiler warnings is not a zero-warning claim about every build component.

Downloaded evidence artifact `11127172716` SHA-256 `3eb47e28ff0a2ea7fe2716fe0a11c561554a71afc5ada143526751f992e2372e`; all five source blob hashes and the actual XML totals were independently checked. Candidate artifact `11127297734` SHA-256 `9e588c2b74bc5f09ef25bb67db5d61c8989cb2cc14b94e5befc0f76f8be1f3e9`.

Tests prove one discovery for 10,000 repeated public accessor reads while every value is freshly invoked, one discovery for repeated absent accessors, recovery after failed callbacks/security/linkage discovery, inherited/default/static access, exact runtime-class separation and concurrent discovery. Slot tests compare 10,000 deterministic mixed layouts with the original stream implementation, exercise all 54 slots, boundaries, large duplicate inputs, order and caller-array isolation.

These are deterministic lookup/behavior checks, not a measured server TPS benchmark, a full IE2 gameplay simulation or a world-upgrade proof. The normal Networks Legacy/United/Gugu matrix must independently pass for the promoted PR. The exact Legacy candidate test supplements that normal matrix; it does not silently replace its default core references or declare reverse saved-world compatibility.

Only tested Java sources and maintenance documentation are promoted. Temporary object-staging/validation workflows and compressed review inputs are not part of this addon change. Version 1.0.45 remains a development candidate until an explicitly validated release bump; no stable release or production installation is performed by this batch.
