# Networks 1.0.57 release candidate

This candidate is intentionally built on the published 1.0.56 integrity baseline.

## Included fixes

- Deterministic multi-controller conflict resolution. Preserve an established controller when possible, choose one
  stable loser otherwise, revalidate before delayed removal, and clear pending-removal state on runtime reset.
- Manual Crafter transaction safety. Cancellable shaped craft events complete before input mutation; shapeless
  recipes fail missing-ingredient preflight and no longer mutate the requirement map while iterating it.
- Auto Crafter availability batching. Aggregated recipe ingredients are checked in one read-only network storage
  traversal while actual withdrawals retain their historical order and rollback boundary.
- Expansion line-transfer full-pass reuse. Customized unlimited/full-distance bidirectional transfers reuse the
  push-resolved menus for the paired grab pass instead of resolving the same line twice in one tick.
- Quantum restart CI hardening. The two-process persistence probe waits a bounded number of ticks for Paper item
  entity registration instead of assuming two ticks is always sufficient.
- Release publication guard. Ordinary master pushes do not overwrite an already-published version; the first
  default-branch build after an intentional version bump may publish the new canonical release.

## Compatibility and behavior boundaries

No item/research IDs, recipes, storage serialization, default line-transfer target budgets, Quantum routing policy,
empty-Quantum assignment policy, or normal controller limits are changed.

The Infinity Dust Extractor changed-source warning has a separate producer-side root cause in InfinityExpansion2:
its GuizhanLib HopperMachine ticker defaults asynchronous. The companion IE2 fix is reviewed separately; Networks
1.0.57 retains the 1.0.56 source-snapshot/compensation protection regardless.

This file describes a release candidate. Publication requires the exact combined head to pass all supported-core,
Java 21 universal-JAR, Purpur 1.21.11 native/restart, and Paper 26.3 native/restart gates.
