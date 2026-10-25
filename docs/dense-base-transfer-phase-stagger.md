# Dense-base transfer tick phase staggering (candidate)

## Production observation

At one large Java Paper base (`world`, chunks `(-333,-348)` and `(-333,-349)`), Slimefun's 10-tick profiling interval reported **526.82ms aggregate callback time**, including **341.17ms** from 129 advanced line-transfer-plus machines and **21.25ms** from 39 advanced line-transfer-plus grabbers. These timings describe measured callback work over ten ticks, **not a single 526ms server tick**.

Merely selecting `pushitem-tick: 4` and `grabitem-tick: 4` would lower average transfer attempts, but the old in-memory counters start at zero for every registered transfer on startup. A large cluster therefore tends to wake all 129 expensive transfer machines on the **same** Slimefun tick, creating periodic overload spikes.

## Change

```yaml
stability:
  transfer-tick-stagger:
    enabled: true

items:
  NTW_EXPANSION_ADVANCED_LINE_TRANSFER_PLUS:
    pushitem-tick: 4
    grabitem-tick: 4
    max-targets-per-tick: 4
  NTW_EXPANSION_ADVANCED_LINE_TRANSFER_PLUS_GRABBER:
    grabitem-tick: 4
    max-targets-per-tick: 4
```

**Merge** these fields into the existing `plugins/Networks/config.yml` sections, rather than replacing the original config or removing `max-distance`, `required-power` and other settings. Saving requires a normal server restart because transfer configuration is cached during registration.

- For intervals **greater than one**, an initial counter is computed from the transfer's block X/Y/Z position. Neighboring transfers spread across the configured 2/3/4/etc. ticker phases instead of all waking together.
- Push and grab use the **same** position-based phase for equal intervals, retaining the existing tick-local full-line traversal reuse optimization.
- Each transfer still attempts the same number of push/grab cycles over a long enough window. It does not change item quantities, network storage APIs, rollback/commit boundaries, transport mode, power charged per attempted transfer or the existing `max-targets-per-tick` budget.
- For `pushitem-tick: 1` or `grabitem-tick: 1`, initial counter **remains zero**, exactly matching historical cadence. Turning `stability.transfer-tick-stagger.enabled: false` restores the old initial phase even for larger intervals.
- A missing/invalid `pushitem-tick` still uses the existing transfer config fallback; this patch does not invent new machine types or modify saved item state.
- No temporary `BlockMenu` or `ItemStack` references are cached across ticker cycles.

## Server acceptance

Run exact-head Legacy, United, and Gugu builds, Paper 26.3 API compile, Java 21 universal JAR build, and native Paper 26.3 build 176/194 storage+rollback probes. Then compare at least three matched `/sf tick top` samples on the same backed-up test world with the machine interval set to 4, with and without staggering. Compare peak and 95th-percentile expensive line-transfer callback timings, actual TPS/MSPT and network item conservation across restarts.

If transport throughput is too slow, reduce `pushitem-tick`/`grabitem-tick` to 2 or 3, or raise `max-targets-per-tick` to 6 or 8. Spread machine locations across adjacent chunks where practical. Never turn off `compatibility.synchronized-machine-tickers` to regain performance: synchronous Bukkit inventory access is required for item safety.

This development branch does not publish a release, modify the official Slimefun 4.1.78 or Networks 1.0.62 release pins, change Geyser/Bedrock behavior or alter any optional resource-pack assets.
