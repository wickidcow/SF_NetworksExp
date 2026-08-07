# Networks Legacy 2.1.112-1.0 — IE2 Runtime Hotfix 1

This hotfix changes Infinity Expansion 2 integration discovery from startup class-name loading to lazy item-instance discovery.

## Why

Some IE2 preview/unofficial builds can be enabled and registered while external plugins cannot safely load the storage implementation class by its upstream class name. The old bridge interpreted that as an incompatible API and disabled IE2 before Networks encountered an actual storage unit.

## New behavior

- InfinityExpansion2 being enabled is enough to register the optional adapter.
- No IE2 `StorageUnit` class is loaded by name during adapter startup.
- The first actual IE2 storage item encountered teaches Networks its runtime storage class.
- Required contract: numeric `getCapacity()`, `int[] getInputSlots()`, and `int[] getOutputSlots()`.
- `getCaches()` is optional. If inaccessible or changed, Networks reads the persisted `stored_amount` as a compatibility fallback.
- Cache reads remain read-only.
- Deposits and withdrawals still go through IE2's real menu input/output slots.
- Networks never directly writes IE2 cache quantities or persisted storage amounts.
- No direct dependency on a Dough `BlockPosition` class is introduced.
- Legacy remains primary; United and Gugu workflow gates remain unchanged.

The shipped plugin version remains `2.1.112-1.0`; this is a runtime compatibility hotfix to the 1.0 source.
