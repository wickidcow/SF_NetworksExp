# Networks Legacy Compatibility Contract

## Release

`1.0.60`, coordinated with InfinityExpansion2 `2.0.15`.

The exact accepted commits, public artifact hashes, native evidence and remaining limitations are recorded in [NETWORKS_EXPANSION_COMPLETION.md](NETWORKS_EXPANSION_COMPLETION.md).

## Maintenance target and retained compatibility gates

**GuGu Slimefun Legacy** is the primary runtime and development target. The published 1.0.60/2.0.15 addon release-native acceptance used `wickidcow/Slimefun-Legacy` source `1cb32d6e95b305998065b8ef6b936a56f679187d`. That historical source and the build pins below remain unchanged.

The existing auxiliary United and Gugu exact-core build gates remain enabled and passed. They preserve the established universal build contract; this release does not undertake new maintenance of abandoned United support. A universal release is not uploaded unless every existing required gate succeeds.

## Coordinated core 4.1.76 candidate

[Core PR #414](https://github.com/wickidcow/Slimefun-Legacy/pull/414) integrated the published pair in r143 at `e7ba40469eae1aa54b4347af19e58341f492edcb`. Its exact-source native, canonical, automatic full-stack and manual [reproducible validation 39625265406](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/39625265406) passed. The verified r143 ZIP replaced the outdated r140 handoff without publishing core 4.1.76.

Core has since advanced through PR #415 and PR #413 to **r145**, source `f472c473b6a2013be29403061785e90d32d1c868`. Its canonical, native, automatic full-stack and final manual [validation 39669276799](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/39669276799) workflows passed, with publication and normalization skipped. Downloaded r145 artifacts and the unchanged strict native/full-stack evidence verifiers now pass, including all 45 addon source/version/hash/CRC records and the unchanged published pair. The verified r145 ZIP successfully replaced the saved r143 handoff with version history preserved. The maintained-tip IE2 test-fixture compile failure is repaired by [PR #36](https://github.com/wickidcow/SF_InfinityExpansion2/pull/36), merged at `b1fd91db9e85189fd108b45e343e32cead9c2dde` after local 183-test and hosted API/build/source-safety acceptance. Its post-merge workflow and the [targeted core maintained-addon retry](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/39666239493/attempts/2) both pass. The original failure, actual successful job/report and separate existing-release cleanup guard refusal are recorded explicitly in the [completion checkpoint](NETWORKS_EXPANSION_COMPLETION.md#current-r145-candidate); r143 evidence does not replace these current-source requirements.

Core publication remains held for explicit maintainer approval of the concrete verified candidate. `publish=false` remains the default. The [core checkpoint](https://github.com/wickidcow/Slimefun-Legacy/blob/master/NETWORKS_EXPANSION_COMPLETION.md), [release tracker #378](https://github.com/wickidcow/Slimefun-Legacy/issues/378), and current Git state determine the live core handoff. Published addon pins and historical acceptance below remain intact. Declared-addon Folia startup checks do not enable Networks or IE2 Folia runtime support.

## Preserved world and addon identity

1.0.60 retains:

- Bukkit plugin name `Networks`
- Main class `io.github.sefiraat.networks.Networks`
- Required plugin name `Slimefun`
- Existing Java package names
- All 289 currently registered item IDs, including the established 2.1.111/Alpha series identities
- Existing Networks and historical persistent-data namespaces
- Existing `CargoStorageUnits.db` file and core tables
- Existing configuration keys, with additive compatibility/Doctor options
- Existing guide categories and the Alpha 2 guide organization

No intentional item-ID, guide, placed-block, or database-file migration is included.

## Three-core build contract

GitHub Actions builds exact core JARs from:

- `wickidcow/Slimefun-Legacy` pinned at `1cb32d6e95b305998065b8ef6b936a56f679187d`, using Java 25 for the core build
- `Slimefun-United/Slimefun-United` `dev`
- `SlimefunGuguProject/Slimefun4` `master`

Networks itself is compiled to Java 21 bytecode against each JAR. The build uses the Paper `1.21.11-R0.1-SNAPSHOT` API baseline and injects the exact core through `-PslimefunCoreJar=/path/to/Slimefun.jar`.

Separate gates compile Networks against Paper 26.3 build 176 and exercise native Paper 26.3 builds 176/194 and Purpur 1.21.11 build 2568. IE2's coordinated storage/machine acceptance is on Purpur; it also passes separate Paper 1.21.11/26.2/26.3 compilation. These results do not certify every future server build or Folia runtime safety.

The release verifier rejects:

- Bundled Slimefun core classes
- Bundled WildStacker/RoseStacker/other optional API classes
- Plugin identity/version drift
- Item-ID count drift
- Java bytecode above 21

## Runtime compatibility behavior

- Legacy is identified by explicit metadata and a non-linking Doctor API marker fallback.
- United is identified by explicit metadata or its unique command aliases.
- Gugu is identified by metadata or its unique API marker.
- United/Gugu-specific fingerprints take precedence over the Legacy fallback marker.
- Unknown core variants fail closed by default.

## Stability behavior

- Optional plugin APIs are fail-soft and may disable themselves without disabling Networks.
- Missing optional integrations do not prevent class loading or startup.
- Scheduled Doctor repair uses a rotating configurable budget rather than a full node scan each pass.
- Manual Doctor scans remain complete for loaded state and never force-load chunks.
- Startup and shutdown clear singleton/cache/ticker/cursor state that could otherwise survive a partial lifecycle.
- Chunk first-tick registration is revalidated and pending locations are cleared during shutdown.
- Alpha 2 database, transfer, cargo, quantum, crafting, remote, and controller protections remain active.

## 1.0 Legacy controller and node stability

- Repeated controller rebuild failures are isolated per controller and paused with a configurable exponential cooldown.
- Failed or replaced runtime roots clear only assignments that still belong to that root; loaded block registrations remain available for rebuilding.
- Controller roots are discarded before their chunk registry is removed, preventing unloaded controllers from remaining globally active.
- Node registration is atomic: same-type duplicate attempts retain the live definition, while type conflicts replace stale identity and invalidate the old root.
- Doctor reports tracked failures, quarantines, circuit trips, duplicate registrations, and node-type conflicts without force-loading chunks.
- All Alpha 2 transfer rollback, crafting rollback, database drain, and persistence protections remain active.

## Required staging checks

1. Back up all worlds and Slimefun/Networks data.
2. Use a full stop/start, not `/reload`.
3. Check that the console reports the expected core family.
4. Run `/networks doctor scan`.
5. Test existing and newly placed network blocks.
6. Test exact amounts through every storage/cargo path.
7. Test blueprints and all automatic crafting machines in use.
8. Test chunk unload/reload and Network Remote revalidation.
9. Restart and compare stored data.
10. Test optional integrations individually and in the same combination used on production.

Folia is unsupported by these addons because multi-chunk transactional networks have not been made safe across independent regions. Neither addon enables Folia with a descriptor flag. This is an addon limitation, not a claim that the pinned Slimefun Legacy core lacks Folia support. Leaf runtime validation is also not established by the 1.0.60/2.0.15 refresh.

## 1.0 persistence additions

The database path and schema remain unchanged. The release adds startup backups, `PRAGMA quick_check`, transactional absolute amount snapshots, and an external `CargoStorageUnits.recovery.tsv` file used only for pending write recovery. Missing configuration keys use safe defaults.

The 1.0.60 repair series adds exact-identity template recovery evidence, retains pending amount writes through rejected database work and clean shutdown, and preserves invalid stored balances for reconciliation. Receipt verification and clean restart tests do not claim atomic recovery across an arbitrary process crash.
