# Auto Crafter batched ingredient availability

## Problem

Auto Crafter recipes aggregate duplicate grid ingredients, but preflight still called
`NetworkRoot.contains` once for every unique ingredient. A full 3x3 recipe could therefore
traverse the same network up to nine times before the first withdrawal, then traverse storage
again for the actual commit. Large loaded crafter counts amplify this read-only work.

## Change

- Add `NetworkRoot.containsAll(ItemRequest[])` for non-mutating multi-requirement preflight.
- Visit the same storage classes used by the existing single-item availability probe once:
  exposed barrels/Quantum/optional storage, crafter outputs, drawers, Advanced Greedy, Greedy,
  and Cells.
- Preserve the existing Infinity Expansion barrel reserve accounting.
- Auto Crafter still builds the same aggregated ingredient plan and exact ItemRequests, but
  performs one batched preflight before any withdrawal.
- Actual withdrawals still use `getItemStack0` in the same ingredient order, so routing,
  storage priority, rollback behavior, crafting throughput and recipe semantics are unchanged.

The batch helper deliberately credits a physical available stack to only one unsatisfied
request. Auto Crafter already aggregates equivalent ingredient identities before this call.

## Validation boundary

Unit coverage checks a successful two-ingredient batch, a shortage, and duplicate-request
non-double-counting. The three-core compatibility matrix and native runtime checks remain
required. Performance improvement should be measured with comparable `/sf tick top`/spark
samples; source inspection alone is not a TPS claim.
