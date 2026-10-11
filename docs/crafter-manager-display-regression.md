# Crafter Manager nested ItemStack regression

The October 11 report uses Slimefun Legacy 4.1.73, Networks 1.0.50 and Purpur
1.21.11 build 2568 (`f57bd86`). `CrafterManager.updateDisplay` fails at line 457
through `DirtyChestMenu` and `ChestMenu.replaceExistingItem` with:

```
ClassCastException: Cannot cast io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack
to org.bukkit.craftbukkit.inventory.CraftItemStack
```

## Cause and correction

The published Networks JAR includes unrelocated Dough `CustomItemStack`. Its
copy constructor calls `super(item)`. Paper 1.21.11's ItemStack copy constructor
stores `item.clone()` as its internal `craftDelegate`. SlimefunItemStack retains
its subclass when cloned. Consequently a CustomItemStack can contain a
SlimefunItemStack delegate, although it is not itself a SlimefunItemStack.

Slimefun Legacy's earlier direct-subclass menu guard is present in 4.1.73 but
does not identify this outer Dough wrapper. CraftInventory then attempts to
cast the nested delegate to CraftItemStack.

Normalize the finished Crafter Manager display icon with the existing
`ItemStackUtil.getCleanItem` immediately before the failing menu write. This
copies the material, count and complete ItemMeta through the material-based
constructor, bypassing the nested delegate. It changes only the display write;
no recipes, crafting throughput, transfer order, item IDs, stored templates or
serialization are changed. The candidate version is 1.0.51 so a future release
does not overwrite the existing 1.0.50 asset.

## Local evidence

Source baseline: `c320adcaec21b945b25e33b303a42ea18007a7fb`.
Runtime: real Purpur 1.21.11 build 2568, Java 25.0.4.1, a fresh disposable world.

| Input | SHA-256 |
| --- | --- |
| Published Slimefun Legacy 4.1.73 | `21a6fed16d673083d09c390ca6dee012802ca003163b27ddf4effba375f20a86` |
| Published Networks 1.0.50 | `e325d9cdbdacb5484b157565aae6c779f868ed8e1db15b79fac9bf076a583ed1` |
| Purpur 1.21.11 build 2568 | `ee199511bf0a970ee8459fe3a351703fec37293f00bc2de1c5cb746c7e4acc34` |
| Local diagnostic candidate | `7e8b75151589e301d701598a15468ebfbd802f18dcf9ef492ef7b48706434731` |

The unmodified published pair reproduced the exact exception through the real
`CrafterManager.updateDisplay`, `DirtyChestMenu` and `CraftInventory`, including
the reported source line numbers. The same fixture passed with the candidate.
It checks 20 repeated Slimefun-output display writes, complete metadata outside
the manager's intentional name/lore presentation, count 7, typed opaque PDC,
custom model data, click-handler installation, dirty tracking, source isolation,
the null-menu path, and an ordinary stone output with count 13.

The local candidate is a diagnostic JAR: the changed manager was compiled with
Java 25, `--release 21`, against the real server libraries and published plugin
JARs. Only `CrafterManager.class` and its four nested classes were replaced in
the published Networks JAR. All other entries are unchanged. It retains the
old version label and is **not** a published or clean Gradle-built 1.0.51 JAR.
The full local Gradle build was blocked while resolving the Shadow plugin.
The source invariant audit and fixture compilation passed. Full builds and
Legacy/United/Gugu tests remain CI gates, separately from this native evidence.

The disposable boots also log external authentication-service failures and the
existing missing localization key `messages.normal-operation.manager.no`.
This fixture establishes the inventory regression result, not an error-free
boot of every feature or a production-world/throughput audit.

## Permanent regression

`tests/runtime/CrafterManagerDisplayProbe.java` calls the real manager with an
isolated one-crafter network, real BlockMenus and a real Bukkit inventory.
Only viewer presence and the root's test crafter set are supplied by the fixture;
the item creation, display method, menu writes and native inventory conversion
execute normally. It shuts down its disposable server after writing a result.
Never install the fixture plugin on a live server.

Run with Java 25 and a new, nonexistent work directory:

```sh
python3 scripts/crafter_manager_runtime_probe.py \
  /path/to/Slimefun.jar /path/to/Networks.jar \
  /path/to/purpur-1.21.11-2568.jar /tmp/new-networks-display-probe
```

The runner verifies the pinned Purpur hash and Mojang cache hash, compiles the
fixture against the supplied plugin/server JARs, records input hashes, and
requires a passing result and normal process exit. CI runs it on the clean
canonical Networks JAR and its tested Legacy core before staging any release
asset, then retains logs and input identities. The three-core compilation and
unit-test matrix remains in place.
