# Experimental Recipe-Aware missing-ingredient fairness

Opt-in Recipe-Aware Network Pushers with multiple ingredient templates may feed
shared input slots unevenly. For example, an eight-batch recipe with 9 iron and
1 gold requires 72 iron; the iron can fill two shared slots before any gold enters.

This experimental patch caps any ingredient to one batch while ANOTHER required
ingredient is not verified in an accepted physical input slot. Once all ingredient
types have a verified input stack, normal 1–64 buffered targets resume.

The scan reads the target's physical INSERT declaration once per pusher tick, only
for multi-template Recipe-Aware mode. Output and display slots are excluded, and
a matching stack must also occupy an item-aware accepted slot for that ingredient.
At most four matching types receive an extra item-aware discovery; each is queried
once. Types left unverified by that budget remain missing. Each attempted transfer
still uses fresh routing and can confirm its own physical input presence with that
same result. The existing four-transfer attempt budget remains unchanged.
It does not expand or override machine-specific safe transport slots.
Non-Recipe-Aware routing, block data, item IDs and templates remain unchanged.

Large template sets may retain the conservative one-batch cap when the discovery
budget cannot verify every ingredient. The configured batch target is a ceiling,
not a guarantee that every machine can fill it each tick. A missing/unsupported
physical input declaration also leaves ingredients unverified. This does not
guarantee completion of an invalid recipe or account for ingredients already
reserved internally by another machine.
An end-to-end busy machine test and tick-performance comparison remain required.

Do not merge with the already validated 1.0.59-rc1 staging candidate until the
three-core Java 21 and native Paper/Purpur tests pass for this change.
