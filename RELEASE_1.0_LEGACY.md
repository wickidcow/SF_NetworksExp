# Networks Legacy 2.1.112 — 1.0 Legacy

## Release goal

This is the first **1.0 Legacy** release of the maintained Networks fork. It consolidates the compatibility, world-safety, integration, controller-lifecycle, transaction, and persistence work completed across Alpha 1 through Alpha 5 without changing the established guide organization.

Slimefun Legacy remains the primary release target. The universal JAR is still blocked unless the same source also compiles and tests against current Slimefun United and Slimefun Gugu builds.

## Preserved upgrade contract

The release intentionally preserves:

- Bukkit plugin name `Networks`
- Main class `io.github.sefiraat.networks.Networks`
- Existing Java packages used by integrations
- All 288 existing Slimefun item IDs
- Existing recipes and guide organization
- Existing placed machines and Slimefun block records
- Existing persistent-data namespaces
- `CargoStorageUnits.db` path and table schema
- Existing controller, grid, blueprint, drawer, and quantum-storage data

No world conversion or destructive database migration is introduced.

## Three-core universal compatibility

The release workflow builds exact current core JARs and gates the universal artifact on:

1. `wickidcow/Slimefun-Legacy` `master` — primary and release compiler
2. `Slimefun-United/Slimefun-United` `dev`
3. `SlimefunGuguProject/Slimefun4` `master`

The final JAR is Java 21 bytecode and is checked to ensure no Slimefun core or optional-integration API classes were accidentally bundled.

## Transaction and item-loss protection

Network transfer helpers use reserve, commit, rollback, and compensation behavior:

- Withdrawals are limited to the destination's reported capacity.
- Any destination remainder is returned to the source network.
- A failed source commit after a deposit triggers a compensating withdrawal from the network.
- Control X compensates its network deposit if the source block cannot be removed or verified as air.
- Rollback failures use a logged world safety-drop only as a final protection against silent item loss.
- Runtime counters report committed movement, rollbacks, compensation, safety drops, and failures through Networks Doctor.

Existing crafting hardening remains in place for exact recipe matching, complete ingredient reservation, consume-before-output behavior, and ingredient rollback.

## Drawer database durability

The existing `CargoStorageUnits.db` remains unchanged, but its write lifecycle is safer:

- A startup backup is created before SQLite opens an existing database.
- Database, WAL, and SHM files are copied together when present.
- Backup retention is bounded and configurable.
- `PRAGMA quick_check` runs before normal startup and fails closed on reported corruption.
- Delayed drawer amount snapshots are committed in one SQLite transaction.
- A small durable atomic `CargoStorageUnits.recovery.tsv` journal records latest absolute amounts before they enter the worker queue; its temporary file is forced before rename, with best-effort directory synchronization.
- Journal replay is idempotent: a crash after SQLite commit but before journal cleanup cannot duplicate an amount delta.
- Shutdown checkpoints any still-unsubmitted amount changes before the database worker stops.

The recovery file is not a replacement database. It contains only pending absolute drawer amounts and deletes itself after successful commits.

## Controller, node, and chunk stability

- Per-controller circuit breakers quarantine repeatedly failing rebuilds with bounded exponential cooldowns.
- Partial runtime roots are discarded after failed builds.
- Successful rebuilds clear prior fault state.
- Node registration is atomic and tracks duplicates/type conflicts.
- Controller roots are removed before chunk registry unload.
- First-tick registration revalidates the loaded chunk, live Slimefun block data, and item type.
- Scheduled Doctor work uses a rotating bounded scan and never force-loads chunks.

## Infinity Expansion 2 storage integration

The optional IE2 bridge supports every storage tier implemented through IE2's shared storage-unit contract:

- Official and relocated/unofficial storage implementations
- Empty-unit first-item deposits
- Matching-item deposits
- Output withdrawals and partial amounts
- Cache amount/capacity reporting
- Nested IE2 storage-unit rejection
- Chunk unload/reload rediscovery

Networks reads IE2 cache state but moves items through IE2's real input/output slots. IE2 remains responsible for its own asynchronous cache, blacklist, void-excess, and persistence rules.

Optional storage support now uses an internal adapter registry so an integration API failure removes only that adapter and leaves native Networks storage operational.

## Doctor diagnostics

`/networks doctor scan` now includes:

- Core/runtime compatibility
- Optional integration states and versions
- Active optional storage adapters
- Controller failures/quarantines/circuit trips
- Node duplicate/type-conflict history
- Drawer cache, pending changes, recovery entries, and save state
- SQLite integrity and startup-backup status
- Database queue scheduled/completed/failed/rejected/cancelled telemetry
- Transfer commits, rollback deficits, compensation deficits, safety drops, and failures

`/networks doctor repair confirm` continues to repair only loaded runtime state and never force-loads chunks.

## Configuration additions

```yaml
database:
  shutdown-timeout-seconds: 15
  integrity-check: true
  recovery-journal: true
  startup-backups:
    enabled: true
    retained: 5
```

Missing keys use these safe defaults, so an existing configuration does not need to be deleted.

## Required qualification before production

1. Back up worlds, `/plugins/Networks`, `/plugins/Slimefun`, and `CargoStorageUnits.db`.
2. Use a complete server stop/start; never `/reload`.
3. Confirm the console reaches the successful Networks enable message.
4. Run `/networks doctor status` and `/networks doctor scan`.
5. Test old Controllers, Grids, Drawers, Quantum Storage, Importers, Exporters, Pushers, Grabbers, wireless/P2P, blueprints, and crafters.
6. Test IE2 empty and populated storage units, matching/mismatched deposits, partial withdrawals, full units, chunk reload, and full restart.
7. Confirm drawer counts before and after a clean stop/restart.
8. Confirm the next startup reports `integrity=ok` and a startup backup path.

The GitHub matrix is the final compile authority for the three current Slimefun core revisions. Production world validation remains required because no automated test can reproduce every server's plugin mix and existing data.
