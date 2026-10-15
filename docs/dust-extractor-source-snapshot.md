# Dust extractor menu-source snapshot follow-up

## Report and evidence boundary

The reported source is an Infinity Dust Extractor. The supplied trace identifies Networks 1.0.55, Slimefun Legacy 4.1.74 and Purpur 1.21.11 build 2568, at world `world`, block `-1314,66,817`. It reports four items accepted and four removed as compensation. It does not identify the installed Infinity Expansion/IE2 version or the source stack's class and metadata.

PR #71 already fixes unconditional VOID source clearing after a rejected or failed deposit. This follow-up addresses a separate, plausible way the unchanged source can fail that check; it does not establish the actual server's trigger.

Inspected primary source: `wickidcow/Slimefun-Legacy`, tag `v4.1.74`, `api/items/SlimefunItemStack.java`. Its `clone()` reconstructs `SlimefunItemStack`, whose constructor writes item identity and invokes `CustomTextureService.setTexture`. That service replaces the first model-data float when the configured mapping is nonzero. Existing metadata differing from the mapping can therefore produce a non-identical clone without any concurrent machine tick. Paper's `ver/1.21.11` `ItemStack(ItemStack)` constructor also delegates to the input's `clone()`, so a copy constructor alone is not a fix.

## Change

- Validate a detached source snapshot before offering any items to the network.
- Keep the normal identity-preserving clone path. If the clone changes identity/count or aliases the original object, copy the existing metadata into a plain stack and accept it only if the strict source comparison succeeds.
- Never silently discard an unrepresentable component. A non-identical fallback or snapshot exception stops before network acceptance, with an explicit pre-deposit warning.
- Derive the menu offer and remainder from the verified snapshot rather than repeatedly cloning the live addon object.
- Retain the strict post-offer type, amount and metadata check, compensation, guarded source restoration and the existing VOID failure result. No ID-only comparison, ignored model data, disabled safeguards, recipe change or persistent-data migration.

## Regression coverage

`NetworkMenuSourceSnapshotTest` adds 14 cases: a rebuilding-clone fixture, ordinary and VOID zero/partial/full acceptance, aliasing clone, real metadata replacement, partial destination failure, source dirty failure, and runtime/linkage snapshot failures. The fixture carries model floats/strings/flags, lore and STRING/LONG/BYTE_ARRAY persistent data. It exercises the production menu entry points; it does not load an actual dust extractor or certify every native component/integration.

Tests are authored here; passing results must be verified against the resulting PR head in CI. Earlier PR checks for `c25223304bca8683be61b389f037a7ca60ee65e6` do not certify this follow-up.

## Release and operational boundary

This remains draft development work with the existing 1.0.55 identity. Do not merge or publish it over the existing v1.0.55 asset. Synchronize a new unused release version and run exact-head validation before approval/publication. No addon bundle or live-server change is part of this follow-up. Keep the affected connection paused pending an approved replacement; do not delete extractor or storage data.
