# Networks #122: Native historical map tint cross-version evidence

**Purpose:** Do not replace Paper 26.3 deprecated `MapMeta.hasColor/getColor/setColor` or `ItemMeta.getDamageResistant` without testing legacy item behavior on both supported runtime generations.

## Exact-source native scenario (test-only)

The universal Networks CI first runs an actual **Purpur 1.21.11 build 2568** server and its two independent JVM storage/restart phases. The synthetic filled-map fixture has an explicit red map tint, quantity **1**, STRING owner sentinel, LONG ledger, and BYTE_ARRAY payload. The build retains the **raw original 1.21.11 `ItemStack.serializeAsBytes`** file as same-run artifact `networks-purpur-1-21-11-quantum-restart`; no real AlbionMC world/player data are included.

Both pinned **Paper 26.3 build 176 and 194** native jobs download that artifact from the **same CI run**, compute its SHA-256 into their input provenance manifest, and stage the file read-only in the disposable Paper plugins directory. The real Paper 26.3 JVM calls `ItemStack.deserializeBytes`, verifies original material, physical count 1, exact typed owner/ledger/byte-array PDC, and clone similarity; invokes the production `ItemDifferenter.calc` in both directions; and asserts the source file bytes and decoded item do not change during diagnosis. The second Paper 26.3 JVM observes the same migrated tint interpretation again.

If the old 1.21.11 raw map cannot be decoded or typed PDC/quantity changes, the native check **fails** rather than treating identical Slimefun IDs or matching visible text as equivalence. The current 26.3 APIs may not expose the old red tint; this check records the observed outcome without assuming tint preservation. It therefore does **not** certify full old→new→old rollback or authorize dropping the deprecated accessor.

## Limitations and next evidence

This is a **synthetic new test fixture generated on the pinned 1.21.11 server**, not an authentic old AlbionMC inventory, shop, machine or storage item. Its success would demonstrate one cross-version serialization and typed-PDC boundary, **not** safety of all custom maps, owner records, skull textures, player shops or quantities through an actual world upgrade and rollback.

Before any production metadata API replacement or release: independently capture genuine backed-up old/fresh item pairs and real 1.21.11→26.3 world/item restoration where feasible, compare serialized field shape, typed PDC, strict `isSimilar`, producer source and no-loss/no-dupe counts, and document any irrecoverable old map-tint semantics. Keep #122 open until those gates pass.

The test changes only CI workflows, probe source and documentation. No change is made to `ItemDifferenter`, `MapUtil`, production metadata constructors, old player/shop items, version numbers, tags, or the Slimefun Legacy 44-addon ZIP.
