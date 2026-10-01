# Grid selection correctness

Continue from Networks master `5dec0d55fb6cd6e1de369f7d0422d9e430856443`, retaining the merged IE2 storage-accessor and metadata preservation work.

## Changes

- Fix an off-by-one in limited forward sort navigation. With four modes the old code skipped ADDON; with three it skipped NUMBER_REVERSE; with two it never left ALPHABETICAL. Backward and unrestricted navigation retain their valid-input results.
- Invalidate only the derived sorted/filtered entry list when the filter or sort choice actually changes. `AbstractGrid.getEntries` otherwise immediately reused the old list when a player requested a display update before the next ticker invalidation.
- Keep the cached view on identical choices and page navigation. Use one private enum array instead of allocating a fresh `values()` array on backward wrapping. This is not a measured server-TPS improvement.

Public setter signatures, enum names/ordinals, page values, history, storage IDs, item quantities/metadata, transfers, recipes, energy costs, production rates and quantum-storage category ordering are unchanged. No saved-data schema or migration is introduced. The changed lists are display views, not storage inventories. This does not redesign ownership/threading of grid rendering or support for arbitrary concurrent callers.

## Validation

`GridCacheTest` adds 17 permanent tests over every supported sort limit, backward parity, inverse navigation and view invalidation/reuse. Entry lists remain caller-owned and unmodified; amounts include a value above exact floating-point integer range. These tests exercise the real GridCache and do not substitute a mock storage system. Actual CI and independent old-code controls must be recorded after execution, not inferred from predecessor success.

No version bump or stable publication in this source patch. Merge only after the normal compatibility/build checks are successful, and coordinate any versioned raw JAR with the core bundle source manifest.
