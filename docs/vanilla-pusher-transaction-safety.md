# Vanilla Pusher special-inventory transaction safety

## Problem

The Vanilla Pusher has special handling for furnace and brewing inventories. Those paths historically wrote a cloned
source stack into the destination first, then cleared the pusher source only when a readback matched.

If a Bukkit or third-party inventory setter wrote an item and then threw, normalized the written item, or refused a
rollback, the destination could retain an item while the unchanged pusher source remained available. That creates an
item-duplication boundary even though the ticker itself is synchronized.

## Change

Furnace fuel/input and brewing fuel/ingredient/bottle writes now use one transaction helper:

1. require an empty destination;
2. clone and write the source;
3. require exact amount and Bukkit metadata similarity before consuming the source;
4. if the readback is different, roll the destination back to empty before preserving the source;
5. if rollback cannot be verified and the destination remains non-empty or unreadable, consume the source
   conservatively rather than knowingly leave the same logical item on both sides.

Setter/read failures are logged. An ambiguous non-empty destination is SEVERE and explicitly reports that the source
was consumed to prevent duplication.

No furnace/brewing recipe logic, slot preference, transfer rate, network routing, item ID, saved data, or permissions
change. The generic ordinary-inventory path is intentionally unchanged in this focused patch and remains a separate
audit target.
