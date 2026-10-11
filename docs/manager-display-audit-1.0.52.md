# Networks 1.0.52 manager display audit

The user expanded the October 11 Crafter Manager report to Quantum, drawers and related manager screens. Networks 1.0.51 already fixes Crafter Manager. The same nested Dough/Slimefun delegate reaches native inventories from additional display writes.

## Reproduced failures and correction

On real Purpur 1.21.11 build 2568 with published Slimefun Legacy 4.1.73, the expanded fixture reproduced the identical `SlimefunItemStack` to `CraftItemStack` cast failure in Quantum Manager, Drawer Manager, Item Flow Viewer main and detail screens, and Linker Grid rendering. The production patch applies the existing `ItemStackUtil.getCleanItem` to the completed display icon at each of those five native inventory writes.

Only four production classes change. The patch leaves recipes, throughput, transfer operations, item/research IDs, storage samples, storage identities, database contents, serialization and optional integration discovery unchanged. It does not replace stored items with fresh templates.

## Native evidence

| Executed path | Baseline | Patched candidate |
| --- | --- | --- |
| Crafter Manager | Passed in 1.0.51 | Passed |
| Quantum Manager | Cast exception | Passed |
| Drawer Manager | Cast exception | Passed |
| Item Flow Viewer main screen | Cast exception | Passed |
| Item Flow Viewer detail screen | Cast exception | Passed |
| Linker Grid display | Cast exception | Passed |
| Drawer stored-item preview | Existing normalization retained | Passed |
| Quantum Storage wrapped-item preview | Passed | Passed |
| Quantum Storage world item display | Passed | Passed |

The manager cases invoke actual registered items and real BlockMenus/CraftInventory. Fixture root subclasses supply isolated crafter, barrel, drawer or recorded-flow entries; viewer presence is supplied so rendering runs. Storage cases check repeated Slimefun displays, typed PDC/custom-model metadata outside intentional decoration, amounts, source isolation, click handlers, dirty tracking, ordinary items, custom Slimefun icons/names, a pinned flag, empty storage and null menus. Drawer container ID/reference/count and quantum stored count are checked for preservation. Preview cases use actual rendering methods, including a real ItemDisplay entity for the world visual.

Linker Grid rendering is exercised with its optional provider absent. This verifies the inventory boundary, not live LogiTech link creation or transfer behavior. Existing native tests and the three-core matrix remain release gates; this is not a claim about every historical world, the user's entire 82-plugin server or Folia region concurrency.

Exact local inputs:

| Input | SHA-256 |
| --- | --- |
| Published Slimefun Legacy 4.1.73 | `21a6fed16d673083d09c390ca6dee012802ca003163b27ddf4effba375f20a86` |
| Published Networks 1.0.51 | `c32a975dba92a4329aeb3845dbfc711d81a10cea4636edf52176ec5111a884c2` |
| Purpur 1.21.11 build 2568 | `ee199511bf0a970ee8459fe3a351703fec37293f00bc2de1c5cb746c7e4acc34` |
| Diagnostic candidate | `30b4edfec8842c38f6b74bf1938326ded0123058f95b18c60358e1ffc98d0dbb` |

Baseline source is merged 1.0.51 commit `9d301906edfd44add8d549a2e98d72cf8fa40648`. Java 25.0.4.1 compiled the four changed classes with `--release 21`; their 17 class files replaced only their matching entries in the published 1.0.51 diagnostic JAR. Every other archive entry is unchanged. The candidate retains the 1.0.51 label and is not a distributable clean 1.0.52 release. All nine native cases passed; source invariants, 503 owned Java-21 classes and 289 item IDs passed verification. The clean Gradle build and Legacy/United/Gugu tests are separately required in CI before publication.

The earlier runner/file names are retained for compatibility: `scripts/crafter_manager_runtime_probe.py` and `tests/runtime/CrafterManagerDisplayProbe.java` now cover this complete table. Run them as described in [the original regression](crafter-manager-display-regression.md). CI runs the expanded fixture on the final clean JAR before release staging and retains console logs and input hashes. Authentication-service failures and the pre-existing missing locale key in disposable logs remain outside this inventory regression result.
