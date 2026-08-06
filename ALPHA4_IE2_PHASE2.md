# Networks Legacy 2.1.112 Alpha 4 — IE2 Integration

## Scope completed

Alpha 4 completes the planned Doctor/integration accuracy work and the Infinity Expansion 2 storage integration phase.

### Doctor integration states

`/networks doctor` now distinguishes:

- `active[version]`
- `not-installed`
- `detected[version][disabled]`
- `incompatible[version]`
- `failed[version][reason]`

An optional integration failure disables only that integration. Networks continues running.

### Infinity Expansion 2 storage support

Networks detects every Infinity Expansion 2 item implemented through IE2's shared `StorageUnit` class. This includes the currently registered storage unit and automatically covers additional capacities/tiers that use the same implementation class.

Supported behavior:

- Storage amount and capacity discovery
- Populated storage units as network output sources
- Empty storage units as network input destinations
- Matching-item deposits
- Partial withdrawals through the live output slot
- IE2 blacklist enforcement through Networks' equivalent blacklist plus nested IE2 storage rejection
- IE2 void-excess and persistence behavior remain owned by IE2
- Fail-soft behavior when IE2 is missing or its internal API changes

## Safety design

IE2's storage ticker is asynchronous. Networks therefore treats IE2's cache as read-only and sends all writes through IE2's actual input and output slots. IE2 remains responsible for validating input, changing the cache, persisting block data, filling the output slot, and applying void-excess behavior.

## Preserved data contract

This update does not change:

- The `Networks` plugin identity
- Existing item IDs (288 preserved)
- Guide organization
- Recipes
- Database paths or schemas
- Existing world block records
- Slimefun Legacy, United, or Gugu compatibility policy

## Runtime test checklist

1. Stop the server completely and install the Alpha 4 JAR.
2. Start with InfinityExpansion2 enabled.
3. Run `/networks doctor` and confirm `InfinityExpansion2=active[version]`.
4. Connect a populated IE2 Storage Unit to a Networks controller and withdraw its stored item.
5. Connect an empty IE2 Storage Unit and deposit its first item through Networks.
6. Test a nearly full unit with void-excess off, then on.
7. Unload/reload the chunk and repeat a deposit and withdrawal.
8. Fully restart and verify the stored amount remains correct.
