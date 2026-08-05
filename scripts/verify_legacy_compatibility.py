#!/usr/bin/env python3
from __future__ import annotations

from collections import Counter
from pathlib import Path
import re
import sys
import yaml

ROOT = Path(__file__).resolve().parents[1]
CJK = re.compile(r"[\u3400-\u9fff]")
TOKEN = re.compile(r"%\d*\$?[a-zA-Z]|%s|\{\d+\}")
ERRORS: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


def read(path: str) -> str:
    file = ROOT / path
    require(file.is_file(), f"missing required file: {path}")
    return file.read_text(encoding="utf-8") if file.is_file() else ""


def token_counter(node) -> Counter:
    counter = Counter()
    if isinstance(node, dict):
        for value in node.values():
            counter.update(token_counter(value))
    elif isinstance(node, list):
        for value in node:
            counter.update(token_counter(value))
    elif isinstance(node, str):
        counter.update(TOKEN.findall(node))
    return counter


def verify_locale_structure(source, translated, path=()):
    label = ".".join(map(str, path)) or "<root>"
    if isinstance(source, dict):
        if not isinstance(translated, dict):
            ERRORS.append(f"English locale type mismatch at {label}")
            return
        for key, value in source.items():
            if key not in translated:
                ERRORS.append(f"missing English locale path: {label}.{key}")
                continue
            verify_locale_structure(value, translated[key], path + (key,))
    elif isinstance(source, list):
        if not isinstance(translated, list) or not translated:
            ERRORS.append(f"English locale list missing at {label}")
            return
        if token_counter(source) != token_counter(translated):
            ERRORS.append(f"placeholder mismatch in list at {label}")
    elif isinstance(source, str):
        if not isinstance(translated, str):
            ERRORS.append(f"English locale string missing at {label}")
        elif Counter(TOKEN.findall(source)) != Counter(TOKEN.findall(translated)):
            ERRORS.append(f"placeholder mismatch at {label}")


build = read("build.gradle.kts")
plugin = yaml.safe_load(read("src/main/resources/plugin.yml")) or {}
config = yaml.safe_load(read("src/main/resources/config.yml")) or {}
locale_text = read("src/main/resources/lang/en-US.yml")
locale = yaml.safe_load(locale_text) or {}
source_locale = yaml.safe_load(read("scripts/localization/zh-CN-source.yml")) or {}
baseline_ids = [line for line in read("compatibility/item-ids-2.1.111.txt").splitlines() if line]
build_workflow = read(".github/workflows/build.yml")
compatibility_workflow = read(".github/workflows/compatibility.yml")
workflow = build_workflow + "\n" + compatibility_workflow
simple_recipe_choice = read("src/main/java/com/balugaq/netex/api/data/SimpleRecipeChoice.java")
wrapper = read("gradle/wrapper/gradle-wrapper.properties")
networks_java = read("src/main/java/io/github/sefiraat/networks/Networks.java")
network_storage = read("src/main/java/io/github/sefiraat/networks/NetworkStorage.java")
query_queue = read("src/main/java/com/ytdd9527/networksexpansion/utils/databases/QueryQueue.java")
data_source = read("src/main/java/com/ytdd9527/networksexpansion/utils/databases/DataSource.java")
blueprint_type = read("src/main/java/io/github/sefiraat/networks/utils/datatypes/PersistentCraftingBlueprintType.java")
stack_utils = read("src/main/java/io/github/sefiraat/networks/utils/StackUtils.java")
storage_unit = read("src/main/java/com/balugaq/netex/api/data/StorageUnitData.java")
inventory_util = read("src/main/java/com/balugaq/netex/utils/InventoryUtil.java")
block_menu_util = read("src/main/java/com/balugaq/netex/utils/BlockMenuUtil.java")
vanilla_pusher = read("src/main/java/io/github/sefiraat/networks/slimefun/network/NetworkVanillaPusher.java")
vanilla_grabber = read("src/main/java/io/github/sefiraat/networks/slimefun/network/NetworkVanillaGrabber.java")
network_remote = read("src/main/java/io/github/sefiraat/networks/slimefun/tools/NetworkRemote.java")
network_root = read("src/main/java/io/github/sefiraat/networks/network/NetworkRoot.java")
networks_drawer = read("src/main/java/com/ytdd9527/networksexpansion/implementation/machines/unit/NetworksDrawer.java")
fluffy_barrel = read("src/main/java/io/github/sefiraat/networks/network/barrel/FluffyBarrel.java")
root_ready_event = read("src/main/java/com/balugaq/netex/api/events/NetworkRootReadyEvent.java")
network_controller = read("src/main/java/io/github/sefiraat/networks/slimefun/network/NetworkController.java")
quantum_cache = read("src/main/java/io/github/sefiraat/networks/network/stackcaches/QuantumCache.java")
quantum_workbench = read("src/main/java/io/github/sefiraat/networks/slimefun/network/NetworkQuantumWorkbench.java")
linker_grid = read("src/main/java/com/balugaq/netex/integrations/logitech/LinkerGrid.java")
doctor = read("src/main/java/io/github/sefiraat/networks/diagnostics/NetworksDoctor.java")
doctor_bridge = read("src/main/java/io/github/sefiraat/networks/diagnostics/LegacyDoctorBridge.java")
runtime_compatibility = read("src/main/java/io/github/sefiraat/networks/compatibility/RuntimeCompatibility.java")
supported_plugins = read("src/main/java/io/github/sefiraat/networks/managers/SupportedPluginManager.java")
localization_service = read("src/main/java/com/ytdd9527/networksexpansion/core/services/LocalizationService.java")
network_object = read("src/main/java/io/github/sefiraat/networks/slimefun/network/NetworkObject.java")
universal_verifier = read("scripts/verify_universal_jar.py")
transfer_utils = read("src/main/java/io/github/sefiraat/networks/utils/NetworkTransferUtils.java")
control_x = read("src/main/java/io/github/sefiraat/networks/slimefun/network/NetworkControlX.java")
quantum_storage = read("src/main/java/io/github/sefiraat/networks/slimefun/network/NetworkQuantumStorage.java")
auto_crafter = read("src/main/java/com/ytdd9527/networksexpansion/core/items/machines/AutoCrafter.java")
smart_crafting = read("src/main/java/com/ytdd9527/networksexpansion/implementation/machines/networks/advanced/SmartNetworkCraftingGridNewStyle.java")
crafting_grid = read("src/main/java/io/github/sefiraat/networks/slimefun/network/grid/NetworkCraftingGrid.java")
crafting_grid_new = read("src/main/java/com/ytdd9527/networksexpansion/implementation/machines/networks/advanced/NetworkCraftingGridNewStyle.java")
recipe_registry = read("src/main/java/com/balugaq/netex/api/helpers/SupportedCraftingTableRecipes.java")
runtime_stability = read("RUNTIME_STABILITY.md")
main_flex_group = read("src/main/java/io/github/sefiraat/networks/slimefun/groups/MainFlexGroup.java")
setup_util = read("src/main/java/com/ytdd9527/networksexpansion/setup/SetupUtil.java")
java_sources = "\n".join(p.read_text(encoding="utf-8") for p in (ROOT / "src/main/java").rglob("*.java"))

# Stable world/plugin identity.
require("Preserved data contract" in runtime_stability
        and "Slimefun Legacy is the primary" in runtime_stability
        and "Do not use `/reload`" in runtime_stability,
        "runtime stability and staging contract is missing")
require(plugin.get("name") == "Networks", "plugin name must remain Networks")
require(plugin.get("main") == "io.github.sefiraat.networks.Networks", "main class changed")
require(plugin.get("depend") == ["Slimefun"], "plugin must depend on Slimefun by its stable plugin name")
require(str(plugin.get("api-version")) == "1.21", "api-version must remain 1.21")
require("networks.commands.doctor" in (plugin.get("permissions") or {}), "Networks Doctor permission is missing")
require(config.get("language") == "en-US", "default language must be en-US")
require(config.get("auto-update") is False, "automatic JAR replacement must remain disabled")
require(config.get("compatibility", {}).get("synchronized-machine-tickers") is True,
        "synchronized machine tickers must be the safe default")
require(config.get("compatibility", {}).get("allow-unknown-slimefun-core") is False,
        "unknown Slimefun cores must fail closed by default")
require(config.get("doctor", {}).get("max-auto-scan-entries") == 512,
        "bounded automatic Doctor scan budget must default to 512 entries")
softdepend = plugin.get("softdepend") or []
for optional_plugin in ["SlimeHUDPlus", "JustEnoughGuide", "LogiTech"]:
    require(optional_plugin in softdepend, f"optional integration is missing from softdepend: {optional_plugin}")

# Java/Paper/exact-core build contract.
require('version = "2.1.112-Legacy-Alpha3"' in build, "project version is not Alpha3")
require("options.release.set(21)" in build, "Java 21 release target is missing")
require("languageVersion.set(JavaLanguageVersion.of(21))" in build, "Java 21 Gradle toolchain is missing")
require("paper-api:1.21.11-R0.1-SNAPSHOT" in build, "Paper 1.21.11 API baseline is missing")
require("compileOnly(files(slimefunCoreJar))" in build, "exact local Slimefun core dependency is missing")
for alias in ["slimefunCoreJar", "SLIMEFUN_CORE_JAR", "slimefunLegacyJar", "SLIMEFUN_LEGACY_JAR", "SLIMEFUN_COMPATIBILITY_JAR"]:
    require(alias in build, f"exact-core dependency alias missing: {alias}")
require("com.github.SlimefunGuguProject:Slimefun4:" not in build, "a remote Gugu core dependency is still present")
require("io.github.thebusybiscuit:Slimefun4:" not in build, "a remote official core dependency is still present")
require("GuizhanLibPlugin" not in build, "GuizhanLibPlugin build dependency is still present")
require("services.gradle.org/distributions/gradle-9.4.1-bin.zip" in wrapper, "official Gradle wrapper URL is missing")
require("extends RecipeChoice.ExactChoice" not in simple_recipe_choice,
        "SimpleRecipeChoice still extends final RecipeChoice.ExactChoice")
require("implements RecipeChoice" in simple_recipe_choice, "SimpleRecipeChoice no longer implements RecipeChoice")

# Three exact Slimefun families.
workflow_invariants = [
    "repository: wickidcow/Slimefun-Legacy",
    "ref: master",
    "repository: Slimefun-United/Slimefun-United",
    "ref: dev",
    "repository: SlimefunGuguProject/Slimefun4",
    'core_java: "25"',
    'core_java: "21"',
    "cache: maven",
    "cache: gradle",
    'name: Set up Java 21 for Networks',
    'java-version: "21"',
    '-PslimefunCoreJar=',
    "verify_legacy_compatibility.py",
    "verify_java21_bytecode.py",
    "workflow_call:",
    "uses: ./.github/workflows/compatibility.yml",
    "needs: compatibility",
    "verify_universal_jar.py",
    "Networks-Legacy-Alpha3-Universal",
]
for required in workflow_invariants:
    require(required in workflow, f"workflow invariant missing: {required}")

# Single-root, native-guide compatibility.
for native_group in [
    "ExpansionItemsMenus.MENU_ITEMS",
    "ExpansionItemsMenus.MENU_CARGO_SYSTEM",
    "ExpansionItemsMenus.MENU_FUNCTIONAL_MACHINE",
    "ExpansionItemsMenus.MENU_TROPHY",
]:
    require(native_group in main_flex_group, f"Networks root guide is missing native expansion link: {native_group}")
require("SlimefunGuide.openItemGroup" in main_flex_group,
        "Networks root guide must use Slimefun's native item-group renderer")
require("MAIN_ITEM_GROUP.register" not in setup_util,
        "duplicate Networks Expansion root category is still registered")
require("SetupUtil::setupMenu" not in setup_util,
        "legacy delayed custom guide registration is still enabled")
require("Main Item Group" not in locale_text and "Sub Menu " not in locale_text,
        "placeholder expansion guide names remain player-facing")

# Runtime compatibility and thread ownership.
require('MINIMUM_MINECRAFT = "1.21.11"' in runtime_compatibility, "Minecraft runtime floor is missing")
require("MINIMUM_JAVA = 21" in runtime_compatibility, "Java runtime floor is missing")
require("SLIMEFUN_LEGACY" in runtime_compatibility, "Legacy runtime detection is missing")
require("SLIMEFUN_UNITED" in runtime_compatibility, "United runtime detection is missing")
require("SLIMEFUN_GUGU" in runtime_compatibility, "Gugu runtime detection is missing")
require('city.norain.slimefun4.api.menu.UniversalMenu' in runtime_compatibility,
        "Gugu runtime marker class is missing")
require('io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctor' in runtime_compatibility,
        "Legacy runtime marker class is missing")
require("hasUnitedCommandAlias" in runtime_compatibility
        and 'normalized.equals("sfu")' in runtime_compatibility
        and 'normalized.equals("slimefununited")' in runtime_compatibility,
        "United runtime alias fingerprint is missing")
require('plugin.getClass().getClassLoader()' in runtime_compatibility,
        "runtime core detection must use the Slimefun plugin classloader")
for forbidden in [
    "runTaskAsynchronously",
    "runTaskLaterAsynchronously",
    "runTaskTimerAsynchronously",
    "scheduleAsyncDelayedTask",
    "scheduleAsyncRepeatingTask",
]:
    require(forbidden not in java_sources, f"unsafe Bukkit asynchronous scheduling remains: {forbidden}")
require("useSynchronizedMachineTickers()" in java_sources, "machine ticker synchronization bridge is missing")
require("DEFAULT_LANGUAGE = \"en-US\"" in networks_java, "Networks default language is not en-US")
require("GuizhanUpdater" not in networks_java, "automatic Guizhan updater code is still present")
require("PinyinHelper" not in java_sources, "Pinyin runtime search remains in Java sources")
require("net.guizhanss.guizhanlib" not in java_sources, "GuizhanLib runtime imports remain in Java sources")

# Runtime registry/database/blueprint hardening.
require("ConcurrentHashMap" in network_storage and "unregisterChunk" in network_storage,
        "thread-safe per-chunk network registry is missing")
require("removeRuntimeState" in java_sources, "controller runtime-state cleanup is missing")
require("Networks-Database-Worker" in query_queue, "single database worker is missing")
require("isWorkerRunning" in query_queue and "shutdown(long timeoutMillis)" in query_queue,
        "bounded database shutdown diagnostics are missing")
require("CREATE UNIQUE INDEX IF NOT EXISTS" in data_source, "drawer uniqueness migration is missing")
require("GROUP BY ContainerID, ItemID" in data_source, "duplicate drawer row merge is missing")
require("ON CONFLICT(ContainerID, ItemID) DO UPDATE" in data_source, "atomic drawer UPSERT is missing")
require("highestUsedId" in data_source, "database counter recovery is missing")
require("readLegacyRecipe" in blueprint_type and "readCurrentRecipe" in blueprint_type,
        "version-tolerant blueprint decoder is missing")
require("BlueprintInstance.INVALID" in blueprint_type, "malformed blueprint fail-closed result is missing")
require("getOwnerProfile()" not in stack_utils and "getOwningPlayer()" not in stack_utils,
        "skull comparison still uses a profile API that can perform remote resolution")
require("getLocallySerializedSkullProfile" in stack_utils and "meta.serialize()" in stack_utils,
        "local-only serialized skull profile comparison is missing")
require("return !Objects.equals(instanceOne.getBaseColor(), instanceTwo.getBaseColor())" in stack_utils,
        "shield metadata comparison still rejects equal shields")
require("source.clone()" in transfer_utils and "root.addItemStack0(accessor, offered)" in transfer_utils,
        "clone-and-commit network transfer helper is missing")
for method in [
    "moveMenuSlotIntoNetwork",
    "moveInventorySlotIntoNetwork",
    "movePlayerCursorIntoNetwork",
    "movePlayerMainHandIntoNetwork",
]:
    require(method in transfer_utils, f"explicit transfer helper missing: {method}")
require("replaceExistingItem(slot, result.remainingStack())" in transfer_utils,
        "menu source-slot commit is missing")
require("sourceInventory.setItem(slot, result.remainingStack())" in transfer_utils,
        "inventory source-slot commit is missing")
require("player.setItemOnCursor" in transfer_utils, "cursor source commit is missing")
require("instanceof InventoryHolder" in control_x,
        "Control X inventory-container rejection is missing")
require(control_x.find("instanceof InventoryHolder") < control_x.find("addItemStack0"),
        "Control X must reject inventory containers before network insertion")
require("invalidateStaleNode" in network_storage and "StorageCacheUtils.getSfItem" in network_storage,
        "lazy stale physical-node invalidation is missing")
require("StorageUnitData.clearAccessHistory(key)" in network_storage
        and "StorageUnitData.clearAllAccessHistory()" in network_storage,
        "drawer access caches are not cleared with runtime network entries")
require("normalizeHistoryLocation" in storage_unit and "clearAllAccessHistory" in storage_unit,
        "normalized drawer access-cache lifecycle is missing")
require("StackUtils.itemsMatch(existing, incoming, true, false)" in inventory_util,
        "inventory insertion still compares partial-stack amounts")
require("blockMenu.markDirty()" in block_menu_util and "slot < 0 || slot >= blockMenu.getSize()" in block_menu_util,
        "BlockMenu transfer persistence/bounds hardening is missing")
require("getSafeTransportSlots" in block_menu_util
        and "AbstractMethodError" in block_menu_util
        and ".distinct()" in block_menu_util,
        "cross-fork Slimefun cargo-slot adapter is missing")
require("BlockMenuUtil.getSafeTransportSlots" in network_root
        and "BlockMenuUtil.getSafeTransportSlots" in fluffy_barrel
        and "BlockMenuUtil.getSafeTransportSlots" in linker_grid,
        "safe transport-slot enumeration is not applied across storage/cargo integrations")
require("blockMenu.markDirty()" in vanilla_pusher and "handleFurnace" in vanilla_pusher
        and "handleBrewingStand" in vanilla_pusher,
        "vanilla cargo pusher persistence verification is missing")
require("inventory instanceof CrafterInventory" in vanilla_pusher
        and "InventoryUtil.addItem(holder.getInventory(), stack)" in vanilla_pusher
        and "stack.getAmount() < before" in vanilla_pusher,
        "vanilla cargo pusher partial-commit/Crafter protection is missing")
require("final ItemStack transfer = stack.clone()" in vanilla_grabber
        and "itemsMatch(committed, transfer, true, true)" in vanilla_grabber
        and "inventory.setItem(sourceSlot, null)" in vanilla_grabber,
        "vanilla cargo grabber clone-verify-commit protection is missing")
require("super(!Bukkit.isPrimaryThread())" in root_ready_event,
        "NetworkRootReadyEvent thread mode is not derived from the actual caller")
require("block.getType() == Material.AIR" in network_controller
        and "!getId().equals(data.getSfId())" in network_controller,
        "stale controller ticker records are not rejected before root rebuild")
require("World world = dropLocation.getWorld()" in transfer_utils
        and "no loaded world was" in transfer_utils,
        "last-resort transfer rollback can still clear an undropped remainder")

quantum_get_start = quantum_storage.find("public static ItemStack getItemStack")
quantum_get_end = quantum_storage.find("public void", quantum_get_start + 1)
quantum_get = quantum_storage[quantum_get_start:quantum_get_end if quantum_get_end > quantum_get_start else None]
require(quantum_get_start >= 0, "Quantum Storage extraction method is missing")
require("withdrawItem" in quantum_get and "syncBlock" in quantum_get,
        "Quantum Storage withdrawal persistence calls are missing")
require(quantum_get.find("withdrawItem") < quantum_get.find("syncBlock"),
        "Quantum Storage must withdraw before synchronizing persistent data")
require("Math.max(Math.max(1L, limit), repairedAmount)" in quantum_cache
        and "Math.max(Math.max(1L, newLimit), this.amount)" in quantum_cache,
        "Quantum Cache limit changes can truncate stored contents")
require("temporarily restore the required capacity" in quantum_storage
        and "cache.restoreAmount(notRestored)" in quantum_storage,
        "Quantum Storage output rollback capacity repair is missing")
require("Fire the event before consuming ingredients" in quantum_workbench
        and "recipeStillPresent" in quantum_workbench
        and "InventoryUtil.give(player, outputRemainder)" in quantum_workbench,
        "Quantum Workbench event/output transaction hardening is missing")
require("LinkReservation" in linker_grid and "rollbackLinkReservation" in linker_grid,
        "LogiTech linker item reservation rollback is missing")
require("new ConcurrentHashMap<>()" in linker_grid
        and "computeIfAbsent" in linker_grid
        and 'icon.split(":", 2)' in linker_grid
        and "StorageCacheUtils.setData(location, BS_LINKER_TYPE" in linker_grid,
        "LogiTech linker cache/icon/type hardening is missing")
require("new ConcurrentHashMap<>()" in networks_drawer
        and "ConcurrentHashMap.newKeySet()" in networks_drawer,
        "Networks Drawer runtime caches are not concurrency-safe")
require("clearAccessHistory" in network_root
        and "accesses.remove(key)" in network_root
        and "clearAllAccessHistory" in network_root,
        "network reverse-access history cleanup is missing")
require("world.isChunkLoaded" in network_remote
        and "Revalidate after the deferred load callback" in network_remote
        and "isGrid(currentItem)" in network_remote,
        "Network Remote stale-grid/chunk revalidation is missing")

require("SupportedCraftingTableRecipes.findRecipe" in smart_crafting,
        "Smart Crafting Grid exact recipe binding is missing")
require("restoreFetchedItems(root, menu, player, got)" in smart_crafting,
        "Smart Crafting Grid ingredient rollback is missing")
require("root.addItemStack(crafted)" not in smart_crafting,
        "Smart Crafting Grid failed-fetch output duplication path remains")
require("size() >= THRESHOLD" in smart_crafting,
        "Smart Crafting Grid entity threshold comparison is reversed")
require("findRecipe" in recipe_registry and "Math.max(input.length, recipe.length)" in recipe_registry,
        "exact full-matrix recipe matching is missing")
require("Consume every exact ingredient before creating the output" in crafting_grid,
        "classic Crafting Grid atomic consume-before-output path is missing")
require("Consume the complete exact matrix before adding the result" in crafting_grid_new,
        "new Crafting Grid atomic multi-craft path is missing")
require("returnItems(root, fetcheds, blockMenu)" in auto_crafter,
        "Auto Crafter ingredient rollback is missing")
require("instance.getItemStack()" in auto_crafter,
        "Auto Crafter output is not bound to its blueprint instance")

# Doctor integration.
require("class NetworksDoctor" in doctor, "Networks Doctor scanner is missing")
require("runAutomaticRepair" in doctor and "automaticNodeCursor" in doctor
        and "Math.floorMod(automaticNodeCursor" in doctor,
        "bounded rotating automatic Doctor scan is missing")
require("isChunkLoaded" in doctor and "loadChunk" not in doctor, "Networks Doctor must not force-load chunks")
require("NetworkQuantumStorage.getCaches()" in doctor and "Stale quantum cache" in doctor,
        "Networks Doctor quantum-storage scan/repair is missing")
require("Proxy.newProxyInstance" in doctor_bridge, "reflective Legacy Doctor bridge is missing")
require("io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctor" in doctor_bridge,
        "Legacy Addon Doctor class name is missing")
doctor_api_package = "io.github.thebusybiscuit.slimefun4.api.diagnostics"
for path in (ROOT / "src/main/java").rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    if doctor_api_package not in text:
        continue
    require(path.name in {"LegacyDoctorBridge.java", "RuntimeCompatibility.java"},
            f"Legacy-only Doctor API is referenced outside the bridge/fingerprint: {path.relative_to(ROOT)}")
    if path.name == "RuntimeCompatibility.java":
        require("import " + doctor_api_package not in text,
                "RuntimeCompatibility must use only a non-linking Legacy marker string")

# Alpha3 lifecycle, optional integration, and unload/reload stability.
require("failStartup" in networks_java and "startupStage" in networks_java,
        "staged fail-safe startup handling is missing")
require("SupportedPluginManager.shutdown()" in networks_java
        and "LocalizationService.clearRuntimeCache()" in networks_java,
        "shutdown singleton/cache reset is missing")
require("runAutomaticRepair(doctorBudget)" in networks_java,
        "automatic Doctor task is not using the bounded scan budget")
require("disableOptionalIntegration" in supported_plugins
        and "initializeDeferredApis" in supported_plugins
        and "runTaskLater" in supported_plugins,
        "fail-soft deferred optional integration initialization is missing")
require("WildStackerAPI.getItemAmount" in supported_plugins
        and supported_plugins.find("WildStackerAPI.getItemAmount") < supported_plugins.find("roseApi.getStackedItem"),
        "WildStacker-first stack ownership policy is missing")
require("setVanillaItemAmount" in supported_plugins
        and "item.setItemStack(stack)" in supported_plugins
        and "item.remove()" in supported_plugins,
        "vanilla item-entity stack commit fallback is missing")
require('com.balugaq.jeg.api.objects.events.GuideEvents' in supported_plugins,
        "JustEnoughGuide API marker validation is missing")
require("ConcurrentHashMap" in localization_service and "clearRuntimeCache" in localization_service,
        "thread-safe localization cache lifecycle is missing")
require("try (InputStream resource" in localization_service
        and localization_service.find("langMap.put") < localization_service.find("languages.add", localization_service.find("langMap.put")),
        "language resources are not safely loaded before registration")
require("PENDING_FIRST_TICK_LOCATIONS" in network_object
        and "PENDING_FIRST_TICK_LOCATIONS.clear()" in network_object
        and "firstTickLocations" not in network_object
        and "world.isChunkLoaded" in network_object,
        "chunk-lifecycle first-tick location cleanup is missing")
require("resetRuntimeState" in doctor
        and networks_java.count("NetworksDoctor.resetRuntimeState()") >= 2,
        "Doctor rotating cursor lifecycle reset is missing")
require("FORBIDDEN_PREFIXES" in universal_verifier
        and "io/github/thebusybiscuit/slimefun4/" in universal_verifier
        and "com/bgsoftware/wildstacker/" in universal_verifier,
        "universal JAR bundled-class guard is missing")

# English locale and item-ID invariants.
require("DisplayNameUtils.getDisplayName(" in java_sources, "Networks-owned item display-name bridge is not in use")
require("DisplayNameUtils.getMaterialName(" in java_sources, "Networks-owned material-name bridge is not in use")
runtime_langs = sorted(p.name for p in (ROOT / "src/main/resources/lang").glob("*.yml"))
require(runtime_langs == ["en-US.yml"], f"unexpected runtime language files: {runtime_langs}")
require(not CJK.search(locale_text), "en-US.yml contains CJK characters")
require("not_enough_items: Not enough items" in locale_text,
        "missing Networks remote/deposit feedback localization")

current_ids = sorted((locale.get("items") or {}).keys())
require(current_ids == baseline_ids, f"item-ID drift detected: expected {len(baseline_ids)}, found {len(current_ids)}")
require(len(current_ids) == 288, f"expected 288 item IDs, found {len(current_ids)}")
verify_locale_structure(source_locale, locale)

allowed_cjk = {ROOT / "scripts/localization/zh-CN-source.yml"}
for base in [ROOT / "src", ROOT / ".github", ROOT / "README.md", ROOT / "LEGACY_COMPATIBILITY.md", ROOT / "CHANGELOG.md"]:
    paths = [base] if base.is_file() else list(base.rglob("*"))
    for path in paths:
        if not path.is_file() or path in allowed_cjk:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if CJK.search(text):
            ERRORS.append(f"player-facing CJK text remains in {path.relative_to(ROOT)}")

if ERRORS:
    print("Networks compatibility verification failed:")
    for error in ERRORS:
        print(" -", error)
    sys.exit(1)

print(
    f"Networks Alpha3 verification passed: {len(current_ids)} item IDs, three-core matrix, "
    "Java 21, Paper 1.21.11, database/runtime/storage/cargo/crafting/remote/doctor hardening."
)
