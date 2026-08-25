# Networks Legacy 2.1.112 Alpha 5 — Controller Stability

## Scope completed

Alpha 5 continues the stability plan after the Alpha 4 Infinity Expansion 2 integration release. It focuses on controller rebuild fault containment, stale runtime-tree cleanup, duplicate node registration protection, and clearer Doctor reporting.

## Controller circuit breaker

A controller rebuild can fail because of malformed block data, a broken optional integration, an event listener, or a changed Slimefun API. Previously the same controller could throw again every Slimefun tick.

Alpha 5 now:

- Records compact per-controller failure state without retaining the throwable.
- Retries transient failures normally until the configured threshold is reached.
- Pauses repeatedly failing controllers with an exponential cooldown.
- Removes a failed partial runtime tree so no stale root remains usable.
- Clears the failure state after a successful rebuild.
- Clears controller safety state when the block breaks, its chunk unloads, or Networks disables.

Default configuration:

```yaml
stability:
  controller-circuit-breaker:
    enabled: true
    failure-threshold: 3
    cooldown-seconds: 30
    maximum-cooldown-seconds: 300
```

## Node registration integrity

The loaded-node registry now uses an atomic registration operation instead of unconditional replacement.

- Same-type duplicate registrations retain the existing live runtime assignment.
- A changed node type replaces the stale definition.
- The old controller tree is discarded after a type conflict.
- Duplicate and conflicting registrations are counted for Doctor diagnostics.
- Replaced controller roots clear only their old node assignments; loaded block registrations remain available for the next rebuild.

## Chunk lifecycle

Controller runtime trees are discarded before their node registry entries are removed during chunk unload. This prevents unloaded controllers from remaining in the global network map and ensures the controller performs its first-tick initialization again after reload.

## Doctor additions

`/networks doctor scan` now reports:

- Tracked controller rebuild failures
- Currently quarantined controllers
- Total rebuild failures and circuit-breaker trips
- Stale controller fault records
- Same-type duplicate node registration attempts
- Node-type conflict replacements

`/networks doctor repair confirm` clears stale controller fault records when the controller block is gone or its chunk is no longer loaded. It does not force-load chunks and does not bypass an active cooldown on a valid controller.

## Preserved data contract

Alpha 5 does not change:

- Bukkit plugin identity
- Any of the 288 Slimefun item IDs
- Guide organization
- Recipes or item definitions
- Existing persistent-data namespaces
- `CargoStorageUnits.db` paths or schemas
- Existing placed blocks or world records
- Infinity Expansion 2 storage behavior from Alpha 4
- Legacy-primary, United-required, and Gugu-required compatibility policy

## Runtime validation

1. Back up the server and perform a complete stop/start.
2. Confirm the console reaches the successful Networks enable message.
3. Run `/networks doctor scan` and record the controller-safety and node-registration lines.
4. Unload and reload chunks containing controllers and verify each network reconnects.
5. Break and replace a controller, then verify no stale root remains in Doctor.
6. Exercise IE2 storage deposits and withdrawals after the controller rebuild.
7. Fully restart and verify network contents and database-backed drawers remain unchanged.
8. If a controller is quarantined, use the Doctor detail to identify the location and exception type before changing the cooldown configuration.
