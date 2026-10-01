# Networks 1.0.47: grid selection correctness

Continue from Networks master `5dec0d55fb6cd6e1de369f7d0422d9e430856443`, retaining the merged IE2 storage-accessor and metadata preservation work. The grid implementation is commit `cf25f72dea946439fc3b9656c00a4e38ed4d0e3c`; the follow-up synchronizes release identities and keeps test reports.

## Changes and preserved boundaries

- Fix an off-by-one in limited forward sort navigation. With four modes the old code skipped ADDON; with three it skipped NUMBER_REVERSE; with two it never left ALPHABETICAL. Backward and unrestricted navigation retain their valid-input results.
- Invalidate only the derived sorted/filtered entry list when the filter or sort choice actually changes. AbstractGrid.getEntries otherwise immediately reused the old list when a player requested a display update before the next ticker invalidation.
- Keep the cached view on identical choices and page navigation. Use one private enum array instead of allocating a fresh values array on backward wrapping. This is not a measured server-TPS improvement.

Public setter signatures, enum names/ordinals, page values, history, storage IDs, item quantities/metadata, transfers, recipes, energy costs, production rates and quantum-storage category ordering are unchanged. No saved-data schema or migration is introduced. The changed lists are display views, not storage inventories. This does not redesign ownership/threading of grid rendering or support arbitrary concurrent callers.

## Actual validation

GridCacheTest adds 17 permanent tests over every supported sort limit, backward parity, inverse navigation and view invalidation/reuse. Entry lists remain caller-owned and unmodified; amounts include a value above exact floating-point integer range. These tests exercise the real GridCache without constructing a fake storage backend.

The normal PR workflow 36937457893 passed its existing Legacy, Gugu and United compile/test lanes plus the final universal-JAR build for the original correction. These checks are not proof of saved-world rollback to every fork.

Independent Legacy-hosted run 36937621805 used the SHA-256-verified published Legacy 4.1.63 and maintained JEG 2.1.67 APIs. Replacing only GridCache with the original source produced 10 assertion failures among the 17 new tests, with no test errors. Restoring the corrected source then passed all 98 project tests without failures, errors or skips, and passed source-contract, Java 21 and universal-JAR checks. Downloaded evidence 11198767844 matched SHA-256 `c9007a780bc7541bcc72f063a90ec98c4409a77c61d4ef7039dd3941b11713f0`; actual XML and archive integrity were inspected.

The versioned candidate was independently built in run 36938244757: all 98 tests passed, the 1.0.47 plugin/JAR identities agreed, Java 21 checks passed and test libraries were absent. Evidence 11197994914 matched SHA-256 `b7a5ef513f7255b2f2e1b83cbc05785c80944248bc740b077615b9774a08998a`. All five promoted release-file blobs, the generated workflow structure and 16 actual XML reports were inspected. Candidate JAR SHA-256: `b9d63570c30f4d0195393b567bddcf48973e1c103302526ecebbb3f67106e9ed`.

The first version-preparation attempt stopped before Gradle because the changelog and existing hardcoded release guards still expected 1.0.46. These checks were updated together, not removed. The final workflow insertion is YAML-parsed and validated. Temporary diagnostic and source-staging workflows are excluded from this branch.

## Release boundary

Version 1.0.47 keeps the existing 1.0.46 release asset intact rather than overwriting it with new behavior. Gradle, both workflows, the existing release guard and changelog move together. The normal PR workflow must pass for the promoted versioned head before merging. The canonical core addon bundle must independently select and validate the new source before publication. No exhaustive live-client, historical-world, machine-throughput or region-concurrency claim is made by these focused tests.
