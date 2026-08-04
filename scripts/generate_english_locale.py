#!/usr/bin/env python3
"""Generate the English-facing Networks locale from the maintained key structure.

Classic Networks item wording follows Sefiraat's Blob Builds release wherever
possible. Expansion-only content uses stable English terminology derived from
its item IDs and behavior. Formatting tokens and placeholders are preserved.
"""
from __future__ import annotations

import copy
import re
from pathlib import Path
from typing import Any
from collections import Counter

import yaml

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "scripts/localization/zh-CN-source.yml"
OUTPUT = ROOT / "src/main/resources/lang/en-US.yml"
CJK_RE = re.compile(r"[\u3400-\u9fff]")
TOKEN_RE = re.compile(r"(?:&(?:#[0-9A-Fa-f]{6}|[0-9A-FK-ORa-fk-orx])|<[^>]+>|%\d*\$?[a-zA-Z]|%s|\{\d+\}|\\n)")

# Original Sefiraat/Blob Builds wording for the core Networks items.
ORIGINAL_ITEMS: dict[str, tuple[str, list[str]]] = {
    "NTW_SYNTHETIC_EMERALD_SHARD": ("Synthetic Emerald Shard", ["A shard of synthetic emerald that", "is the backbone for information", "transference."]),
    "NTW_OPTIC_GLASS": ("Optic Glass", ["A simple glass that is able to", "transfer small bits of information."]),
    "NTW_OPTIC_CABLE": ("Optic Cable", ["A simple wire that is able to", "transfer large bits of information."]),
    "NTW_OPTIC_STAR": ("Optic Star", ["A crystalline star structure that", "can transfer large bits of information."]),
    "NTW_RADIOACTIVE_OPTIC_STAR": ("Radioactive Optic Star", ["A crystalline star structure that", "can store insane amounts of information."]),
    "NTW_SHRINKING_BASE": ("Shrinking Base", ["An advanced construct able to make", "big things go small."]),
    "NTW_SIMPLE_NANOBOTS": ("Simple Nanobots", ["Teeny tiny little bots that can", "help you with precise tasks."]),
    "NTW_ADVANCED_NANOBOTS": ("Advanced Nanobots", ["Teeny tiny little bots that can", "help you with precise tasks.", "This version is smarter and faster."]),
    "NTW_AI_CORE": ("A.I. Core", ["A burgeoning artificial intelligence", "resides within this weak shell."]),
    "NTW_EMPOWERED_AI_CORE": ("Empowered A.I. Core", ["A flourishing artificial intelligence", "resides within this shell."]),
    "NTW_PRISTINE_AI_CORE": ("Pristine A.I. Core", ["A perfected artificial intelligence", "resides within this defined shell."]),
    "NTW_INTERDIMENSIONAL_PRESENCE": ("Interdimensional Presence", ["An artificial intelligence that has", "grown too powerful for just a", "single dimension."]),
    "NTW_CONTROLLER": ("Network Controller", ["The Network Controller is the brain", "for the whole network. Max 1 per network."]),
    "NTW_BRIDGE": ("Network Bridge", ["The bridge allows you to cheaply", "connect network objects together."]),
    "NTW_MONITOR": ("Network Monitor", ["Allows the network to interact with", "an adjacent supported inventory.", "", "Supports Network Shells and compatible", "addon storage implementations."]),
    "NTW_IMPORT": ("Network Importer", ["Moves items placed inside it into", "the network, up to 9 stacks per", "Slimefun tick.", "Accepts items from Cargo."]),
    "NTW_EXPORT": ("Network Exporter", ["Can be configured to constantly", "export one stack of a chosen item.", "Its inventory can be accessed by Cargo."]),
    "NTW_GRABBER": ("Network Grabber", ["Tries to grab the first available", "item from the selected Slimefun machine."]),
    "NTW_PUSHER": ("Network Pusher", ["Tries to push a matching item from", "the network into the selected machine."]),
    "NTW_MOREPUSHER": ("Network Pusher (Enhanced)", ["Pushes matching items from the network", "into the selected machine at a higher rate."]),
    "NTW_BESTPUSHER": ("Network Pusher (Ultimate)", ["Pushes matching items from the network", "into the selected machine at the best rate."]),
    "NTW_CONTROL_X": ("Network Control: X", ["Cuts a supported vanilla block out", "of the world and stores it in the network.", "Only works on blocks without inventories."]),
    "NTW_CONTROL_V": ("Network Control: V", ["Pastes a stored vanilla block from", "the network back into the world."]),
    "NTW_VACUUM": ("Network Vacuum", ["Collects nearby dropped items and", "tries to insert them into the network."]),
    "NTW_VANILLA_GRABBER": ("Network Vanilla Grabber", ["Pulls the first available item from", "the selected vanilla inventory."]),
    "NTW_VANILLA_PUSHER": ("Network Vanilla Pusher", ["Pushes items supplied to this node", "into the selected vanilla inventory."]),
    "NTW_NETWORK_WIRELESS_TRANSMITTER": ("Network Wireless Transmitter", ["Transmits matching items to a linked", "Network Wireless Receiver in this world.", "Use the Wireless Configurator to link it."]),
    "NTW_NETWORK_WIRELESS_RECEIVER": ("Network Wireless Receiver", ["Receives items from a linked wireless", "transmitter and inserts them into", "its connected network."]),
    "NTW_TRASH": ("Network Purger", ["Pulls matching items from the network", "and permanently voids them.", "Use with great care!"]),
    "NTW_GRID": ("Network Grid", ["Displays every item stored in the network", "and allows direct insertion or withdrawal."]),
    "NTW_CRAFTING_GRID": ("Network Crafting Grid", ["A Network Grid with a crafting area", "that crafts using items stored directly", "inside the network."]),
    "NTW_CELL": ("Network Cell", ["A double-chest-sized inventory that", "can be accessed from the network", "or opened directly in the world."]),
    "NTW_GREEDY_BLOCK": ("Network Greedy Block", ["Reserves one stack for a selected item.", "If that stack is full, additional matching", "items will not enter the network."]),
    "NTW_QUANTUM_WORKBENCH": ("Network Quantum Workbench", ["Allows the crafting of Quantum Storages."]),
    "NTW_POWER_DISPLAY": ("Network Power Display", ["Displays the power stored in the network.", "Simple, right?"]),
    "NTW_RECIPE_ENCODER": ("Network Recipe Encoder", ["Creates a Crafting Blueprint from", "the items placed in its recipe area."]),
    "NTW_AUTO_CRAFTER": ("Network Auto Crafter", ["Accepts a Crafting Blueprint and", "automatically crafts requested items", "using materials from the network."]),
    "NTW_AUTO_CRAFTER_WITHHOLDING": ("Network Auto Crafter (Withholding)", ["Automatically crafts from a blueprint", "and keeps one stack in its output.", "That output remains visible to the network", "and can also be accessed by Cargo."]),
    "NTW_CRAFTING_BLUEPRINT": ("Crafting Blueprint", ["A blank blueprint used to store", "a crafting recipe."]),
    "NTW_PROBE": ("Network Probe", ["Use on a controller to show every", "node connected to the network."]),
    "NTW_REMOTE": ("Network Remote", ["Opens a bound grid wirelessly.", "The grid's chunk must be loaded."]),
    "NTW_REMOTE_EMPOWERED": ("Empowered Network Remote", ["Opens a bound grid wirelessly.", "The grid's chunk must be loaded."]),
    "NTW_REMOTE_PRISTINE": ("Pristine Network Remote", ["Opens a bound grid wirelessly", "with unlimited range in the same world."]),
    "NTW_REMOTE_ULTIMATE": ("Ultimate Network Remote", ["Opens a bound grid wirelessly", "across dimensions."]),
    "NTW_CRAYON": ("Network Crayon", ["Use on a controller to toggle working", "particle effects for network nodes."]),
    "NTW_CONFIGURATOR": ("Network Configurator", ["Copies and pastes the configuration", "of directional network interfaces."]),
    "NTW_WIRELESS_CONFIGURATOR": ("Network Wireless Configurator", ["Stores a receiver location and applies", "it to a Wireless Transmitter."]),
    "NTW_RAKE_1": ("Network Rake (1)", ["Right-click a Network object to", "break it instantly.", "", "250 uses remaining"]),
    "NTW_RAKE_2": ("Network Rake (2)", ["Right-click a Network object to", "break it instantly.", "", "1,000 uses remaining"]),
    "NTW_RAKE_3": ("Network Rake (3)", ["Right-click a Network object to", "break it instantly.", "", "9,999 uses remaining"]),
    "NTW_DEBUG_STICK": ("Network Debug Stick", ["Right-click a Network object to", "toggle diagnostic logging."]),
}

EXACT: dict[str, str] = {
    "成功": "Success",
    "空": "Empty",
    "页": "Page",
    "警告": "Warning",
    "错误": "Error",
    "通知": "Notice",
    "网络": "Networks",
    "单击此处": "Click here",
    "研究": "Research",
    "合成材料": "Crafting Material",
    "机器": "Machine",
    "工具": "Tool",
    "装置": "Mechanism",
    "燃料": "Fuel",
    "材料": "Material",
    "配方类型": "Recipe Type",
    "指南": "Guide",
    "未设置名称": "Name not configured",
    "未设置物品描述": "Lore not configured",
    "正在加载数据": "Loading data",
    "工作中": "Working",
    "输出槽已满": "Output is full",
    "没有足够的空间": "Not enough space",
    "没有足够的物品": "Not enough items",
    "没有足够的网络能源": "Not enough network power",
    "网络中没有足够的物品": "Not enough items in the network",
    "网络中没有足够的原材料": "Not enough resources in the network",
    "未连接网络": "No network connected",
    "未找到目标网络": "Target network not found",
    "不能向同一网络发送物品": "Items cannot be sent to the same network",
    "无效的蓝图": "Invalid blueprint",
    "蓝图已损坏": "Blueprint is damaged",
    "不支持的蓝图": "Unsupported blueprint",
    "没有放置蓝图": "No blueprint inserted",
    "没有输入物": "No input items",
    "未找到物品": "Item not found",
    "未找到容器": "Inventory not found",
    "未找到位置": "Location not found",
    "没有权限": "No permission",
    "受保护的方块": "Protected block",
    "无效的配方": "Invalid recipe",
    "未找到有效配方": "No valid recipe found",
    "未找到原版配方": "No vanilla recipe found",
    "输出物品": "Output Item",
    "已指定配方": "Encoded Recipe",
    "保存成功!": "Saved successfully!",
    "发生错误": "An error occurred",
    "能源已满": "Energy buffer is full",
    "初始化方向": "Initializing direction",
    "未设置方向": "No direction configured",
    "不被允许的物品": "Item is not allowed",
    "不被允许的方块": "Block is not allowed",
    "不是蓝图": "This item is not a blueprint",
    "结果过大": "Result is too large",
    "无物品请求": "No item request",
    "未找到菜单": "Menu not found",
    "无法访问代码": "Unable to access code",
    "周围实体过多": "Too many nearby entities",
    "网络量子工作台": "Network Quantum Workbench",
    "支持拼音搜索": "Supports pinyin search",
    "目前支持:": "Currently supports:",
    "排序方式: ": "Sort mode: ",
    "网络电容可以接收来自": "The Network Capacitor can receive power from",
    "能源网络的电力并存储起来": "the EnergyNet and store it",
    "以供其他网络设备使用": "for other network devices to use.",
    "在量子奇点中存储大量物品": "Stores mass quantities of items inside a quantum singularity.",
    "一张空白的蓝图": "A blank blueprint",
    "你也可以直接放入或取出物品": "You can also insert or withdraw items directly.",
    "右键点击一个网络节点": "Right-click a Network node",
    "可以立即破坏": "to break it instantly.",
    "远程打开绑定的网格": "Opens a bound grid remotely.",
    "需要加载网格所在区块": "The grid's chunk must be loaded.",
    "指定的机器中抓取第一个物品": "Grabs the first available item from the selected machine.",
    "传输至指定的机器中.": "Transfers items into the selected machine.",
    "网络推送器会尝试将": "The Network Pusher will try to",
    "网桥用于连接不同的网络物品": "Network Bridges connect network objects",
    "来形成一个完整的网络": "to form one complete network.",
}

# Terms used to create readable names from stable item IDs.
TOKEN_NAMES = {
    "NTW": "Network", "EXPANSION": "Expansion", "AI": "A.I.", "INFO": "Information",
    "QG": "Quantum Grid", "HUD": "HUD", "JEG": "JEG", "P2P": "P2P", "ID": "ID",
    "MOREPUSHER": "Enhanced Pusher", "BESTPUSHER": "Ultimate Pusher", "ORDINAL": "Standard",
    "CRAFTING": "Crafting", "WORKBENCH": "Workbench", "GRIND": "Grind", "STONE": "Stone",
    "ORE": "Ore", "CRUSHER": "Crusher", "PRESSURE": "Pressure", "CHAMBER": "Chamber",
    "WITHHOLDING": "Withholding", "VANILLA": "Vanilla", "WHITELISTED": "Whitelisted",
    "CARGO": "Cargo", "QUANTUM": "Quantum", "STORAGE": "Storage", "UNIT": "Unit",
    "AUTOMATIC": "Automatic", "AUTO": "Auto", "CRAFT": "Craft", "CRAFTER": "Crafter",
    "LINE": "Line", "TRANSFER": "Transfer", "PUSHER": "Pusher", "GRABBER": "Grabber",
    "PURGER": "Purger", "GREEDY": "Greedy", "BRIDGE": "Bridge", "MONITOR": "Monitor",
    "IMPORT": "Importer", "EXPORT": "Exporter", "GRID": "Grid", "CELL": "Cell",
    "CAPACITOR": "Capacitor", "POWER": "Power", "OUTLET": "Outlet", "DISPLAY": "Display",
    "RECIPE": "Recipe", "ENCODER": "Encoder", "BLUEPRINT": "Blueprint", "REMOTE": "Remote",
    "CONFIGURATOR": "Configurator", "WIRELESS": "Wireless", "TRANSMITTER": "Transmitter",
    "RECEIVER": "Receiver", "SMART": "Smart", "ADVANCED": "Advanced", "BETTER": "Improved",
    "BEST": "Ultimate", "MORE": "Enhanced", "PLUS": "Plus", "DRAWER": "Drawer",
    "MANAGER": "Manager", "VIEWER": "Viewer", "STATUS": "Status", "ITEM": "Item",
    "FLOW": "Flow", "SWITCHING": "Switching", "HANGING": "Hanging", "LINKER": "Linker",
    "PLACEHOLDER": "Placeholder", "DIFFERENTER": "Differentiator", "FACING": "Facing",
    "PRESETTER": "Preset Tool", "SUPER": "Super", "TRASH": "Purger", "VACUUM": "Vacuum",
    "MAGIC": "Magic", "ARMOR": "Armor", "FORGE": "Forge", "SMELTERY": "Smeltery",
    "ANCIENT": "Ancient", "ALTAR": "Altar", "COMPRESSOR": "Compressor", "JUICER": "Juicer",
    "OFFSETTER": "Offset Tool", "DUE": "Dual", "MACHINE": "Machine", "DECODER": "Decoder",
    "MOVER": "Mover", "TOOL": "Tool", "UPGRADE": "Upgrade", "TABLE": "Table", "MODEL": "Model",
    "AUTHOR": "Contributor", "ANNOUNCE": "Information", "QUICK": "Quick", "ONLY": "Only",
    "INPUT": "Input", "OUTPUT": "Output", "WHITE": "White", "LIGHT": "Light", "GRAY": "Gray",
    "BLACK": "Black", "BROWN": "Brown", "RED": "Red", "ORANGE": "Orange", "YELLOW": "Yellow",
    "LIME": "Lime", "GREEN": "Green", "CYAN": "Cyan", "BLUE": "Blue", "PURPLE": "Purple",
    "MAGENTA": "Magenta", "PINK": "Pink", "SEFIRAAT": "Sefiraat", "YBW0014": "YBW0014",
    "YITOUDAIDAI": "Yitoudaidai", "TINALNESS": "Tinalness", "CRAYON": "Crayon",
    "PROBE": "Probe", "RAKE": "Rake", "DEBUG": "Debug", "STICK": "Stick", "CONTROL": "Control",
    "SYNTHETIC": "Synthetic", "EMERALD": "Emerald", "SHARD": "Shard", "OPTIC": "Optic",
    "CABLE": "Cable", "STAR": "Star", "RADIOACTIVE": "Radioactive", "SHRINKING": "Shrinking",
    "BASE": "Base", "SIMPLE": "Simple", "NANOBOTS": "Nanobots", "EMPOWERED": "Empowered",
    "PRISTINE": "Pristine", "INTERDIMENSIONAL": "Interdimensional", "PRESENCE": "Presence",
    "NEW": "New", "STYLE": "Style", "CUSTOM": "Custom", "AMOUNT": "Amount",
}

MESSAGE_OVERRIDES: dict[str, Any] = {
    "messages.guide.click-to-research": "&7Click to unlock this item",
    "messages.guide.cost": "Cost: ",
    "messages.guide.cost-level": " levels",
    "messages.keybind.title": "&aSelect the keybind set you want to edit",
    "messages.keybind.sub-title": "&aClick the keybind action you want to change",
    "messages.keybind.action-select-title": "&aSelect the action for this keybind",
    "messages.keybind.scripts.upload": "&aEnter a name for your keybind script",
    "messages.keybind.scripts.copied": "&aKeybind script copied",
    "messages.keybind.scripts-title": "&aKeybind Control Center",
    "messages.blueprint.title": "<click_info>Encoded Recipe",
    "messages.blueprint.empty": "<passive>Empty",
    "messages.blueprint.output": "<click_info>Output Item",
    "messages.commands.no-permission": "&cYou do not have permission to use this command!",
    "messages.commands.no-item-in-hand": "&cYou must hold an item in your hand!",
    "messages.commands.missing-required-argument": "&cMissing required argument: <%s>",
    "messages.commands.invalid-required-argument": "&cInvalid required argument: <%s>",
    "messages.commands.invalid-argument": "&cInvalid argument: <%s>",
    "messages.commands.must-be-player": "&cThis command can only be used by a player!",
    "messages.commands.unknown-error": "&cAn unknown error occurred!",
    "messages.commands.must-admin-debuggable": "&cYou must look at an AdminDebuggable block to use this command!",
    "messages.commands.viewer-added": "&aYou were added to this block's diagnostic viewers.",
    "messages.commands.viewer-removed": "&aYou were removed from this block's diagnostic viewers.",
    "messages.commands.invalid-quantum-storage": "<error>This is not a valid Quantum Storage.",
    "messages.commands.must-look-at-quantum-storage": "&cYou must look at a Network Quantum Storage!",
    "messages.commands.must-look-at-drawer": "&cYou must look at a Network Drawer!",
    "messages.commands.invalid-drawer": "<error>This Network Drawer is missing or damaged.",
    "messages.commands.updated-drawer": "&aThe stored item was updated.",
    "messages.commands.must-hand-item": "&cYou must hold an item to use this command!",
    "messages.commands.must-hand-blueprint": "<error>You must hold a Crafting Blueprint.",
    "messages.commands.invalid-blueprint": "<error>Unable to read the blueprint data.",
    "messages.commands.fixed-blueprint": "<success>The blueprint was repaired.",
    "messages.commands.updated-quantum-storage": "<success>The stored item was updated.",
    "messages.commands.must-hand-quantum-storage": "<error>You must hold a Quantum Storage.",
    "messages.commands.no-set-item": "<error>This Quantum Storage has no item assigned or is damaged.",
    "messages.commands.not-a-slimefun-item": "<error>This is not a Slimefun item.",
    "messages.commands.cannot-update-cargo-storage-unit": "<error>Updating Network Drawers is not supported here.",
    "messages.commands.updated-item": "<success>The item was updated.",
    "messages.commands.updated-item-in-quantum-storage": "<success>The stored item was updated!",
    "messages.commands.wait-for-data": "<green>Data requested. Please wait...",
    "messages.commands.set-container-id": "&aSet the Network Drawer at %s to container ID %s.",
    "messages.commands.invalid-slot-index": "<error>The slot must be between 0 and %s!",
    "messages.commands.empty-slot": "<error>That slot is empty!",
    "messages.commands.selected-area-outline-hide-request": "&aRequested that the selected-area outline be hidden.",
    "messages.commands.selected-area-outline-show-request": "&aRequested that the selected-area outline be shown.",
    "messages.commands.clear-selected-pos": "&aCleared the selected positions.",
    "messages.startup.loaded-language": "Loaded language file.",
    "messages.startup.getting-config": "Loading configuration...",
    "messages.startup.trying-auto-update": "Automatic updates are disabled in the Slimefun Legacy fork.",
    "messages.startup.connecting-database": "Connecting to the Networks database...",
    "messages.startup.failed-to-connect-database": "Failed to connect to the Networks database.",
    "messages.startup.creating-query-queue": "Starting the database query queue...",
    "messages.startup.creating-auto-save-thread": "Starting the drawer auto-save task...",
    "messages.startup.registering-items": "Registering Networks items...",
    "messages.startup.registering-listeners": "Registering Networks listeners...",
    "messages.startup.registering-commands": "Registering Networks commands...",
    "messages.startup.enabled-successfully": "Networks enabled successfully.",
    "messages.shutdown.saving-config": "Saving Networks configuration...",
    "messages.shutdown.disconnecting-database": "Closing the Networks database...",
    "messages.shutdown.saving-data": "Waiting for %s pending data operations...",
    "messages.shutdown.saved-all-data": "All Networks data was saved.",
    "messages.shutdown.disabled-successfully": "Networks disabled successfully.",
    "messages.save-all": "Saving Networks data.",
    "messages.depend.not-found-guizhanlib": "GuizhanLibPlugin was not found.",
    "messages.depend.suggest-download-guizhanlib": "This Legacy build no longer requires GuizhanLibPlugin for updates.",
    "messages.depend.suggest-download-newer-slimefun": "Install the supported Slimefun Legacy release.",
    "messages.normal-operation.common.page": "Page",
    "messages.normal-operation.common.crafter_types.ancient_altar": "Ancient Altar",
    "messages.normal-operation.common.crafter_types.armor_forge": "Armor Forge",
    "messages.normal-operation.common.crafter_types.compressor": "Compressor",
    "messages.normal-operation.common.crafter_types.crafting": "Crafting Table",
    "messages.normal-operation.common.crafter_types.expansion_workbench": "Networks Expansion Workbench",
    "messages.normal-operation.common.crafter_types.grind_stone": "Grind Stone",
    "messages.normal-operation.common.crafter_types.juicer": "Juicer",
    "messages.normal-operation.common.crafter_types.magic_workbench": "Magic Workbench",
    "messages.normal-operation.common.crafter_types.ore_crusher": "Ore Crusher",
    "messages.normal-operation.common.crafter_types.pressure_chamber": "Pressure Chamber",
    "messages.normal-operation.common.crafter_types.quantum_workbench": "Network Quantum Workbench",
    "messages.normal-operation.common.crafter_types.smeltery": "Smeltery",
    "messages.normal-operation.common.crafter_types.ae": "Advanced Enchantments",
}


POLISHED_MESSAGES: dict[str, str] = {
    # Normal operation
    "messages.normal-operation.common.page": "Page",
    "messages.normal-operation.common.crafter_types.ancient_altar": "Ancient Altar",
    "messages.normal-operation.common.crafter_types.armor_forge": "Armor Forge",
    "messages.normal-operation.common.crafter_types.compressor": "Compressor",
    "messages.normal-operation.common.crafter_types.crafting": "Crafting Table",
    "messages.normal-operation.common.crafter_types.expansion_workbench": "Networks Expansion Workbench",
    "messages.normal-operation.common.crafter_types.grind_stone": "Grind Stone",
    "messages.normal-operation.common.crafter_types.juicer": "Juicer",
    "messages.normal-operation.common.crafter_types.magic_workbench": "Magic Workbench",
    "messages.normal-operation.common.crafter_types.ore_crusher": "Ore Crusher",
    "messages.normal-operation.common.crafter_types.pressure_chamber": "Pressure Chamber",
    "messages.normal-operation.common.crafter_types.quantum_workbench": "Network Quantum Workbench",
    "messages.normal-operation.common.crafter_types.smeltery": "Smeltery",
    "messages.normal-operation.common.crafter_types.ae": "Advanced Enchantments",
    "messages.normal-operation.quantum_cache.empty": "Empty",
    "messages.normal-operation.quantum_cache.stored_item": "<click_info>Stored item: %s",
    "messages.normal-operation.quantum_cache.stored_amount": "<click_info>Stored amount: <white>%s",
    "messages.normal-operation.quantum_cache.custom_max_limit": "<click_info>Capacity limit: <error>%s",
    "messages.normal-operation.memory_card.empty": "<warning>Empty",
    "messages.normal-operation.item_mover.stored_item": "&bStored item: %s",
    "messages.normal-operation.item_mover.stored_amount": "&bStored amount: %s",
    "messages.normal-operation.item_mover.suggest_use_drawers": "&cUse the Network Drawer's quick-transfer mode.",
    "messages.normal-operation.quantum_storage.waiting_for_input_custom_max_amount": "<passive>[<gold>Networks Expansion<passive>] <warning>Enter a capacity from 1 to 140737488355328.",
    "messages.normal-operation.quantum_storage.waiting_for_input_custom_max_amount_new": "<passive>[<gold>Networks Expansion<passive>] <warning>Enter a capacity from 1 to 140737488355328.",
    "messages.normal-operation.grid.waiting_for_filter": "<warning>Enter an item display name or material to filter by.",
    "messages.normal-operation.grid.item_amount": "{0}Amount: {1}{2}",
    "messages.normal-operation.grid.filter": "&aCurrent search: %s",
    "messages.normal-operation.grid_new_style.click_to_withdraw": "<passive>Click to withdraw this item",
    "messages.normal-operation.grid_new_style.crafted": "<success>Last crafted: <click_info>%s x%s",
    "messages.normal-operation.directional.limit_quantity": "<random_color>Transfer amount: %s",
    "messages.normal-operation.directional.transport_mode": "<random_color>Transfer mode: %s",
    "messages.normal-operation.directional.display_empty": "&7Set direction: %s",
    "messages.normal-operation.directional.display_name": "<passive>Direction: %s (%s)",
    "messages.normal-operation.directional.preset_display_name": "<passive>Click to preset direction: %s",
    "messages.normal-operation.directional.set_facing": "<success>Direction set to this inventory.",
    "messages.normal-operation.manager.set_name": "<passive>Enter a name: ",
    "messages.normal-operation.manager.id": "<passive>ID: %s",
    "messages.normal-operation.manager.location": "<click_info>%s:%s:%s",
    "messages.normal-operation.manager.advance": "<passive>Advanced crafter: %s",
    "messages.normal-operation.manager.crafter_type": "<passive>Crafter type: %s",
    "messages.normal-operation.manager.True": "&a✔",
    "messages.normal-operation.manager.False": "&c✘",
    "messages.normal-operation.viewer.location": "&7%s:%s:%s",
    "messages.normal-operation.viewer.when": "&7Time: %s",
    "messages.normal-operation.viewer.change": "%s",
    "messages.normal-operation.comprehensive.auto_set_face": "Automatically set direction",

    # Completed operations
    "messages.completed-operation.directional.presetted_facing": "<success>Saved preset direction: %s",
    "messages.completed-operation.directional.presetted": "<success>Preset direction changed to: %s",
    "messages.completed-operation.directional.presetted_failed": "<warning>No compatible machine was found for this direction preset.",
    "messages.completed-operation.comprehensive.toggled_filter_mode": "<success>Filter mode changed to: %s",
    "messages.completed-operation.comprehensive.filter_mode_black_list": "Blacklist",
    "messages.completed-operation.comprehensive.filter_mode_white_list": "Whitelist",
    "messages.completed-operation.comprehensive.toggled_match_mode": "<success>Match mode changed to: %s",
    "messages.completed-operation.comprehensive.match_mode_all_match": "Exact item match",
    "messages.completed-operation.comprehensive.match_mode_material_match": "Material-only match",
    "messages.completed-operation.comprehensive.facing_preset_applied": "<success>Direction preset applied automatically.",
    "messages.completed-operation.status_viewer.subscribed": "<success>Now watching machine (%s).",
    "messages.completed-operation.status_viewer.unsubscribed": "<success>Stopped watching machine (%s).",
    "messages.completed-operation.status_viewer.is_networks_object": "<success>This is a Networks node.",
    "messages.completed-operation.status_viewer.not_networks_object": "<warning>This is not a Networks node.",
    "messages.completed-operation.status_viewer.connected_to_network": "<success>Connected to a network.",
    "messages.completed-operation.status_viewer.not_connected_to_network": "<warning>Not connected to a network.",
    "messages.completed-operation.decoder.decode_blueprint_success": "&aBlueprint decoded successfully.",
    "messages.completed-operation.wireless_configurator.transmitter_linked": "<success>Wireless Transmitter linked.",
    "messages.completed-operation.wireless_configurator.receiver_set": "<success>Wireless Receiver location saved.",
    "messages.completed-operation.wireless_configurator.set_location": "Set machine %s target location to %s.",
    "messages.completed-operation.cargo_node_quick_tool.node_set": "&9Stored node type: %s",
    "messages.completed-operation.cargo_node_quick_tool.config_saved": "&aCargo node configuration loaded.",
    "messages.completed-operation.cargo_node_quick_tool.pasted_config": "&aCargo node configuration applied.",
    "messages.completed-operation.grid.filter_set": "<success>Search filter enabled.",
    "messages.completed-operation.grid.sort_orders.alphabetical": "Sort alphabetically",
    "messages.completed-operation.grid.sort_orders.number": "Sort by item amount",
    "messages.completed-operation.grid.sort_orders.number_reverse": "Sort by item amount, descending",
    "messages.completed-operation.grid.sort_orders.addon": "Sort alphabetically by addon",
    "messages.completed-operation.quantum_storage.changed_custom_max_amount": "<passive>[<gold>Networks Expansion<passive>] <success>Capacity changed to: %s",
    "messages.completed-operation.remote.bound_to_grid": "<success>Network Grid bound to this remote.",
    "messages.completed-operation.probe.split": "------------------------------",
    "messages.completed-operation.probe.networks_title": "         Networks - Node Statistics         ",
    "messages.completed-operation.probe.expansion_title": "    Networks Expansion - Node Statistics    ",
    "messages.completed-operation.probe.distinct_items": "Distinct item types",
    "messages.completed-operation.probe.total_items": "Total stored items",
    "messages.completed-operation.probe.total_nodes": "Total nodes: %s/%s",
    "messages.completed-operation.probe.overburdened": "<error>Warning: <passive>This network reached its node limit. Some nodes may stop working; remove unnecessary nodes.",
    "messages.completed-operation.crayon.enabled": "<success>Network particles enabled.",
    "messages.completed-operation.crayon.disabled": "<warning>Network particles disabled.",
    "messages.completed-operation.configurator.copied": "<success>Configuration copied.",
    "messages.completed-operation.configurator.copied_limit_quantity": "<success>Saved transfer amount: %s",
    "messages.completed-operation.configurator.pasted_limit_quantity": "<success>Applied transfer amount: %s",
    "messages.completed-operation.configurator.copied_transport_mode": "<success>Saved transfer mode: %s",
    "messages.completed-operation.configurator.pasted_transport_mode": "<success>Applied transfer mode: %s",
    "messages.completed-operation.configurator.pasted_facing": "<success>Direction: <passive>Applied successfully.",
    "messages.completed-operation.configurator.pasted_item": "<success>Item [%s]: <passive>Added to the filter.",
    "messages.completed-operation.drawer.bound_id": "&9Bound container ID: &e%s",
    "messages.completed-operation.drawer.server-uuid": "&7Server ID: %s",
    "messages.completed-operation.drawer.deposited_item": "&aItem deposited.",
    "messages.completed-operation.drawer.transferred_to_quantum_storage": "&aTransferred to Quantum Storage.",
    "messages.completed-operation.drawer.transferred_to_drawer": "&aDeposited %sx%s into the Network Drawer.",
    "messages.completed-operation.drawer.transferred_to_item_mover": "&aTransferred %sx%s into the Item Mover.",
    "messages.completed-operation.item_mover.deposit_success": "Stored %sx%s in the Item Mover.",
    "messages.completed-operation.item_mover.withdraw_success": "Transferred %sx%s into storage.",
    "messages.completed-operation.manager.top_storage_off": "<warning>Storage unpinned.",
    "messages.completed-operation.manager.top_storage_on": "<success>Storage pinned.",
    "messages.completed-operation.manager.set_icon": "<success>Display icon updated.",
    "messages.completed-operation.manager.set_name": "<success>Name updated.",
    "messages.completed-operation.manual_crafter.success": "&aCrafting completed.",
    "messages.completed-operation.storage_card_converter.success": "&aStorage Card conversion completed.",
    "messages.completed-operation.viewer.linked-stack": "&aEntangled-link history refreshed.",

    # Unsupported or failed operations
    "messages.unsupported-operation.incompatible-jeg-version": "<error>Your JustEnoughGuide version is too old. Install the latest compatible build.",
    "messages.unsupported-operation.storage_card_converter.final_tech_required": "<error>FinalTECH or FinalTECH-Changed is required to use this machine.",
    "messages.unsupported-operation.storage_card_converter.research_required": "<error>You have not unlocked the Storage Card research.",
    "messages.unsupported-operation.storage_card_converter.card_disabled": "<error>Storage Cards are disabled in this world.",
    "messages.unsupported-operation.storage_card_converter.output_full": "<error>The output slot is full.",
    "messages.unsupported-operation.storage_card_converter.no_quantum_storage": "<error>Insert a Quantum Storage.",
    "messages.unsupported-operation.storage_card_converter.quantum_storage_not_single": "<error>Insert exactly one Quantum Storage.",
    "messages.unsupported-operation.storage_card_converter.not_quantum_storage": "<error>The inserted item is not a Quantum Storage.",
    "messages.unsupported-operation.storage_card_converter.too_few_items": "<error>The Quantum Storage does not contain enough items.",
    "messages.unsupported-operation.storage_card_converter.not_target_item": "<error>The stored item cannot be converted into a Storage Card.",
    "messages.unsupported-operation.controller.cancel_place": "<error>This network already has a Network Controller.",
    "messages.unsupported-operation.comprehensive.cancel_place": "<error>Two Network Controllers cannot be connected.",
    "messages.unsupported-operation.comprehensive.no_permission": "&cYou do not have permission to use this item.",
    "messages.unsupported-operation.comprehensive.invalid_queue": "Unable to queue this task. Restart the server and try again.",
    "messages.unsupported-operation.comprehensive.unable_to_auto_set_face": "&cUnable to set a direction automatically at x:{} y:{} z:{}.",
    "messages.unsupported-operation.decoder.no_input": "&cInsert a Crafting Blueprint.",
    "messages.unsupported-operation.decoder.not_blueprint": "&cThe inserted item is not a Crafting Blueprint.",
    "messages.unsupported-operation.decoder.unsupported_blueprint": "&cThis blueprint type is not supported.",
    "messages.unsupported-operation.decoder.invalid_blueprint": "&cThis item does not contain valid blueprint data.",
    "messages.unsupported-operation.decoder.output_full": "&cClear the output slot first.",
    "messages.unsupported-operation.storage_unit_upgrade_table.no_recipe_match": "&cNo matching upgrade recipe was found.",
    "messages.unsupported-operation.wireless_configurator.not_network_wireless_block": "<error>Use this tool on a Networks wireless block.",
    "messages.unsupported-operation.wireless_configurator.no_target_location": "<error>Set a receiver location first.",
    "messages.unsupported-operation.wireless_configurator.not_same_world": "<error>The Wireless Receiver is in another world.",
    "messages.unsupported-operation.item_mover.invalid_item_mover": "&cThis is not a valid Item Mover.",
    "messages.unsupported-operation.item_mover.invalid_storage": "&cSelect a valid storage device.",
    "messages.unsupported-operation.item_mover.empty_storage": "&cThe storage contains no available items.",
    "messages.unsupported-operation.item_mover.invalid_item": "&cUnable to read the stored item.",
    "messages.unsupported-operation.item_mover.empty_mover": "&cThe Item Mover is empty.",
    "messages.unsupported-operation.item_mover.full_storage": "&cThe target storage is full.",
    "messages.unsupported-operation.item_mover.suggest_use_drawers": "&cUse this item inside the Network Drawer interface.",
    "messages.unsupported-operation.encoder.not_enough_power": "<warning>The network does not have enough power to encode this recipe.",
    "messages.unsupported-operation.encoder.invalid_blueprint": "<warning>Insert a valid blank Crafting Blueprint.",
    "messages.unsupported-operation.encoder.disabled_blueprint": "<warning>This blueprint is disabled.",
    "messages.unsupported-operation.encoder.disabled_output": "<warning>This recipe output is disabled.",
    "messages.unsupported-operation.encoder.invalid_recipe": "<warning>The arranged items do not form a valid recipe.",
    "messages.unsupported-operation.encoder.output_full": "<warning>Clear the output slot first.",
    "messages.unsupported-operation.cargo_node_quick_tool.invalid_tool": "&cThis tool is invalid. Do not use it in a stack.",
    "messages.unsupported-operation.cargo_node_quick_tool.invalid_node": "&cLook at a Cargo node first.",
    "messages.unsupported-operation.cargo_node_quick_tool.not-enough-items": "&cYou do not have enough materials.",
    "messages.unsupported-operation.cargo_node_quick_tool.node-type-not-same": "&cThe saved node type does not match the target node.",
    "messages.unsupported-operation.cargo_node_quick_tool.no-config": "&cLoad a Cargo node configuration first.",
    "messages.unsupported-operation.display.unknown_type": "Unknown display group type '%s'; custom models were disabled.",
    "messages.unsupported-operation.memory_card.blacklisted_item": "<warning>This item cannot be stored on a Memory Card.",
    "messages.unsupported-operation.memory_card.invalid_amount": "<warning>Use one Memory Card at a time; do not stack them.",
    "messages.unsupported-operation.memory_card.not_empty": "<warning>Only an empty Memory Card can be assigned a new item.",
    "messages.unsupported-operation.can_cooldown": "<warning>The Network Probe is still cooling down.",
    "messages.unsupported-operation.drawer.wrong_server": "<warning>Use this item on the correct server. Drawer server: %s; current server: %s.",
    "messages.unsupported-operation.drawer.already_exists": "&cThis container already exists at another location.",
    "messages.unsupported-operation.drawer.invalid_container": "&cInsert a Quantum Storage or Item Mover into the storage slot.",
    "messages.unsupported-operation.drawer.invalid_container_amount": "&cOnly one item may be placed in the storage slot.",
    "messages.unsupported-operation.drawer.invalid_chosen_item": "&cPlace the item you want to transfer in the selection slot.",
    "messages.unsupported-operation.drawer.invalid_quantum_storage": "&cThe Quantum Storage is empty, invalid, or damaged.",
    "messages.unsupported-operation.drawer.quantum_storage_item_mismatch": "&cThe Quantum Storage contains a different item.",
    "messages.unsupported-operation.drawer.quantum_storage_full": "&cThe Quantum Storage cannot provide or accept the requested amount.",
    "messages.unsupported-operation.drawer.not_enough_item": "&cThis container does not have enough items to transfer.",
    "messages.unsupported-operation.drawer.each_not_enough_item": "&cThere are no more items available to transfer.",
    "messages.unsupported-operation.drawer.not_found_chosen_item": "&cItem not found: %s",
    "messages.unsupported-operation.drawer.item_mover_empty": "&cThe Item Mover contains no items.",
    "messages.unsupported-operation.drawer.item_mover_item_mismatch": "&cThe Item Mover contains a different item.",
    "messages.unsupported-operation.directional.unexcepted_value": "Unexpected value: %s",
    "messages.unsupported-operation.remote.must_connect_to_grid": "<error>Bind this remote to a Network Grid first.",
    "messages.unsupported-operation.remote.not_a_grid_found": "<error>Unable to find the bound Network Grid.",
    "messages.unsupported-operation.remote.grid_not_in_range": "<error>The bound Network Grid is out of range.",
    "messages.unsupported-operation.remote.no_grid_bound": "<error>This remote is not bound to a Network Grid.",
    "messages.unsupported-operation.remote.grid_not_loaded": "<error>The bound Network Grid's chunk is not loaded.",
    "messages.unsupported-operation.quantum_storage.quantum_storage_not_empty": "<warning>Quantum Storage must be empty before changing its stored item.",
    "messages.unsupported-operation.quantum_storage.custom_max_amount_not_supported": "<warning>This is not a valid Advanced Quantum Storage.",
    "messages.unsupported-operation.quantum_storage.invalid_custom_max_amount": "<passive>[<gold>Networks Expansion<passive>] <error>The capacity must be from 1 to 140737488355328.",
    "messages.unsupported-operation.quantum_storage.invalid_custom_max_amount_new": "<passive>[<gold>Networks Expansion<passive>] <error>The capacity must be from 1 to 140737488355328.",
    "messages.unsupported-operation.quantum_storage.may_duping": "Player '%s' attempted an unsafe Quantum Storage operation at %s. The action was blocked.",
    "messages.unsupported-operation.quantum_storage.dangerous_operation": "<error>Unsafe configuration blocked: do not connect Quantum Storage and a Network Cell to the same operation.",
    "messages.unsupported-operation.configurator.not_a_copyable_block": "<error>This block has no direction configuration to copy.",
    "messages.unsupported-operation.configurator.not_a_pasteable_block": "<error>Look at a directional Networks block before pasting.",
    "messages.unsupported-operation.configurator.facing_not_found": "<error>Direction: <passive>Not provided.",
    "messages.unsupported-operation.configurator.not_enough_items": "<warning>Item [%s]: <passive>Not enough items to fill the filter.",
    "messages.unsupported-operation.configurator.no_item_configured_pusher": "<warning>Item [%s]: <passive>No item was saved in this pusher configuration.",
    "messages.unsupported-operation.configurator.no_item_configured": "<warning>Item: <passive>No item was saved in this configuration.",
    "messages.unsupported-operation.debugger.player_is_not_op": "<error>Only server operators may use this item.",
    "messages.unsupported-operation.quantum_workbench.output_slot_full": "<warning>Clear the output slot first.",
    "messages.unsupported-operation.grid.may_duping": "Player '%s' attempted to open a Network Grid from an invalid node at %s. The action was blocked.",
    "messages.unsupported-operation.manager.support_quantum_only": "&cThis operation only supports Network Quantum Storage.",
    "messages.unsupported-operation.manager.quantum_storage_not_empty": "<warning>The Network Quantum Storage must be empty before changing its item.",
    "messages.unsupported-operation.manual_crafter.disabled-output": "&cThis recipe output is disabled.",
    "messages.unsupported-operation.manual_crafter.full-output": "&cThe output slot is full.",
    "messages.unsupported-operation.crafter_manager.different_type_1": "&cThe stored blueprint does not match this crafter type.",
    "messages.unsupported-operation.crafter_manager.different_type_2": "&cStored type: %s; &aExpected type: %s",
    "messages.unsupported-operation.viewer.location-not-found": "&cUnable to find this item's location.",
    "messages.unsupported-operation.viewer.link-stack-not-found": "&cNot enough Hyperlinks or Quantum Entangled Singularities are available.",
    "messages.unsupported-operation.viewer.no-permission": "&cYou do not have permission to access that storage block.",
    "messages.unsupported-operation.viewer.cannot-access-code": "&cThe required integration code is unavailable; the setting was not changed.",
    "messages.unsupported-operation.viewer.not-initialized": "&cUnable to initialize the integration. Contact the plugin maintainer.",
}
MESSAGE_OVERRIDES.update(POLISHED_MESSAGES)

GROUPS = {
    "main": "<main>Networks",
    "materials": "<main>Crafting Materials",
    "tools": "<main>Network Management Tools",
    "network_items": "<main>Network Machines",
    "network_quantums": "<main>Quantum Storage",
    "disabled_items": "<main>Disabled / Removed Items",
}

THEME = {
    "gold": "", "white": "", "aqua": "", "warning": "Warning", "error": "Error",
    "notice": "Notice", "passive": "", "success": "Success", "main": "Networks",
    "click_info": "Click", "research": "Research", "crafting": "Crafting Material",
    "machine": "Machine", "tool": "Tool", "mechanism": "Mechanism", "fuel": "Fuel",
    "material_class": "Material", "recipe_type": "Recipe Type", "guide": "Guide",
    "name_not_found": "Name not configured", "lore_not_found": "Lore not configured",
    "model": " &f(&aModel&f)",
}


def humanize(value: str) -> str:
    words = []
    for token in value.replace("-", "_").split("_"):
        if not token:
            continue
        if token.isdigit():
            words.append(token)
        else:
            words.append(TOKEN_NAMES.get(token.upper(), token.replace("x", "X").title()))
    result = " ".join(words)
    result = result.replace("Network Network", "Network").replace("Expansion Expansion", "Expansion")
    result = result.replace("Grind Stone", "Grind Stone")
    return result.strip()


def item_name(item_id: str) -> str:
    if item_id in ORIGINAL_ITEMS:
        return ORIGINAL_ITEMS[item_id][0]
    base = item_id
    if base.startswith("NTW_EXPANSION_"):
        base = base[len("NTW_EXPANSION_"):]
        prefix = "Network Expansion "
    elif base.startswith("NTW_"):
        base = base[len("NTW_"):]
        prefix = "Network "
    else:
        prefix = ""
    name = humanize(base)
    if name.startswith("Network ") and prefix == "Network ":
        prefix = ""
    if "Contributor" in name:
        prefix = ""
    if item_id.startswith("NTW_QUANTUM_STORAGE_"):
        tier = item_id.rsplit("_", 1)[-1]
        return f"Network Quantum Storage ({tier})"
    if item_id.startswith("NTW_CAPACITOR_"):
        return f"Network Capacitor ({item_id.rsplit('_',1)[-1]})"
    if item_id.startswith("NTW_POWER_OUTLET_"):
        return f"Network Power Outlet ({item_id.rsplit('_',1)[-1]})"
    if item_id.startswith("NTW_EXPANSION_CARGO_STORAGE_UNIT_"):
        suffix = item_id[len("NTW_EXPANSION_CARGO_STORAGE_UNIT_"):]
        return f"Network Cargo Storage Unit {suffix.replace('_MODEL', ' Model')}"
    if item_id.startswith("NTW_EXPANSION_LINE_POWER_OUTLET_"):
        return f"Network Line Power Outlet {item_id.rsplit('_',1)[-1]}"
    if item_id.startswith("NTW_EXPANSION_BRIDGE_"):
        return f"Network Bridge ({humanize(item_id[len('NTW_EXPANSION_BRIDGE_'):])})"
    if item_id.startswith("NTW_EXPANSION_ANNOUNCE_"):
        return "Networks Expansion Information"
    return (prefix + name).strip()


def generic_item_lore(item_id: str, name: str) -> list[str]:
    u = item_id.upper()
    if "AUTHOR_" in u:
        return ["Contributed to the continued development", "and maintenance of Networks Expansion."]
    if "ANNOUNCE" in u:
        return ["Information about this Networks Expansion build."]
    if "WORKBENCH_BLUEPRINT" in u or u.endswith("_BLUEPRINT"):
        return [f"Stores a {name.replace(' Blueprint','')} recipe", "for use by a compatible Network crafter."]
    if "RECIPE_ENCODER" in u:
        return [f"Encodes recipes for the {name.replace(' Recipe Encoder','')}.", "Insert the recipe and a blank blueprint."]
    if "AUTO_" in u and any(x in u for x in ("WORKBENCH", "FORGE", "SMELTERY", "ALTAR", "COMPRESSOR", "JUICER", "CRUSHER", "CHAMBER", "CRAFTING")):
        return ["Automatically crafts the encoded recipe", "using materials stored in the network."] + (["Keeps one output stack available."] if "WITHHOLDING" in u else [])
    if "LINE_TRANSFER" in u or "_TRANSFER" in u:
        action = "moves items between connected inventories"
        if "PUSHER" in u: action = "pushes items into the selected inventory"
        elif "GRABBER" in u: action = "pulls items from the selected inventory"
        scope = "vanilla inventories" if "VANILLA" in u else "Slimefun-compatible inventories"
        return [f"Continuously {action}", f"using the Networks system and {scope}."]
    if "SMART_GRABBER" in u:
        return ["Pulls matching items from the selected", "machine using configurable filters."]
    if "SMART_PUSHER" in u:
        return ["Pushes matching items into the selected", "machine using configurable filters."]
    if "GRID" in u:
        return ["Displays items available in the connected", "network and allows direct interaction."]
    if "MONITOR" in u:
        return ["Connects an adjacent compatible inventory", "to the Networks storage system."]
    if "IMPORT" in u:
        return ["Moves items from its inventory into", "the connected network."]
    if "EXPORT" in u:
        return ["Moves selected items from the network", "into its accessible output inventory."]
    if "PURGER" in u or "TRASH" in u:
        return ["Permanently removes matching items", "from the connected network.", "Use with care!"]
    if "GREEDY" in u:
        return ["Reserves network capacity for a selected", "item and rejects excess matching items."]
    if "CAPACITOR" in u:
        return ["Stores Network power for connected", "machines and network operations."]
    if "POWER_OUTLET" in u:
        return ["Transfers stored Network power to", "an adjacent EnergyNet machine."]
    if "QUANTUM_STORAGE" in u:
        return ["Stores a massive quantity of one item", "inside a quantum singularity."]
    if "CARGO_STORAGE_UNIT" in u or "DRAWER" in u:
        return ["A high-capacity storage unit exposed", "to Networks and compatible Cargo systems."]
    if "MANAGER" in u:
        return ["Provides a management interface for", "connected Networks devices."]
    if "VIEWER" in u:
        return ["Displays live diagnostic information", "for connected Networks devices."]
    if "CONFIGURATOR" in u or "PRESETTER" in u or "INFO_TOOL" in u:
        return ["A Networks administration tool for", "configuring and inspecting devices."]
    if "BRIDGE" in u:
        return ["Connects nearby Network nodes together", "to extend the same network."]
    if "VACUUM" in u:
        return ["Collects nearby dropped items and", "inserts them into the network."]
    if "LINKER" in u:
        return ["Links compatible Networks devices", "for remote access and management."]
    if "ITEM_MOVER" in u:
        return ["Moves stored items between compatible", "Networks storage devices."]
    if "DECODER" in u:
        return ["Reads and displays the recipe stored", "inside a Crafting Blueprint."]
    if "STORAGE_UPGRADE_TABLE" in u:
        return ["Upgrades compatible Networks storage", "devices while preserving stored items."]
    if "QUICK_TOOL" in u:
        return ["Quickly configures compatible Cargo", "nodes for Networks integration."]
    if "PLACEHOLDER" in u:
        return ["Internal placeholder item."]
    return [f"A component of the Networks Expansion system.", f"Use the Slimefun guide to learn how {name} works."]


def leading_format(value: str) -> str:
    m = re.match(r"^((?:&(?:#[0-9A-Fa-f]{6}|[0-9A-FK-ORa-fk-orx])|<[^>]+>)+)", value)
    return m.group(1) if m else ""


def preserve_tokens(source: str, translated: str) -> str:
    required = Counter(
        token for token in TOKEN_RE.findall(source)
        if token.startswith("%") or token.startswith("{") or token == "\\n"
    )
    present = Counter(TOKEN_RE.findall(translated))
    for token, count in required.items():
        missing = count - present[token]
        if missing > 0:
            translated += " " + " ".join([token] * missing)
    prefix = leading_format(source)
    if prefix and not translated.startswith(prefix):
        translated = prefix + translated
    return translated


def translate_exact_or_key(path: tuple[str, ...], value: str) -> str:
    dotted = ".".join(path)
    if dotted in MESSAGE_OVERRIDES:
        return str(MESSAGE_OVERRIDES[dotted])
    if value in EXACT:
        return preserve_tokens(value, EXACT[value])

    # Common phrase substitutions. These are intentionally conservative.
    result = value
    replacements = [
        ("左键点击", "Left-click"), ("右键点击", "Right-click"), ("Shift+左键点击", "Shift + Left-click"),
        ("Shift+右键点击", "Shift + Right-click"), ("点击", "Click"), ("按Q", "Press Q"),
        ("打开存储", "Open storage"), ("高亮存储", "Highlight storage"), ("设置存储的名字", "Rename storage"),
        ("设置朝向", "Set direction"), ("打开目标方块", "Open target block"), ("当前数量", "Current amount"),
        ("当前模式", "Current mode"), ("当前容量限制", "Current capacity limit"), ("数量", "Amount"),
        ("存储物品", "Stored item"), ("物品", "item"), ("存储", "storage"), ("网络", "network"),
        ("输出", "output"), ("输入", "input"), ("合成", "craft"), ("配方", "recipe"),
        ("蓝图", "blueprint"), ("方向", "direction"), ("目标", "target"), ("方块", "block"),
        ("已启用", "Enabled"), ("已禁用", "Disabled"), ("启用", "Enable"), ("禁用", "Disable"),
        ("添加", "Add"), ("移除", "Remove"), ("清空", "Clear"), ("返回", "Back"),
        ("上一页", "Previous page"), ("下一页", "Next page"), ("搜索", "Search"),
        ("过滤", "Filter"), ("白名单", "Whitelist"), ("黑名单", "Blacklist"),
        ("全部匹配", "Match all"), ("材料匹配", "Match material"), ("工作中", "Working"),
        ("状态", "Status"), ("容量", "Capacity"), ("范围", "Range"), ("无限", "Unlimited"),
        ("跨世界", "Cross-dimensional"), ("原版", "vanilla"), ("粘液", "Slimefun"),
        ("网络拓展", "Networks Expansion"), ("高级", "Advanced"), ("快速", "Quick"),
        ("智能", "Smart"), ("设置", "Set"), ("未设置", "Not configured"),
        ("已设置", "Configured"), ("没有", "No "), ("未找到", "Not found: "),
        ("无效", "Invalid"), ("错误", "Error"), ("成功", "Success"), ("失败", "Failed"),
        ("正在加载", "Loading"), ("正在保存", "Saving"), ("已保存", "Saved"),
        ("必须", "must"), ("不能", "cannot"), ("不支持", "Unsupported"), ("支持", "Supports"),
        ("需要", "Requires"), ("默认", "Default"), ("当前", "Current"), ("模式", "mode"),
        ("运输", "transfer"), ("抓取", "pull"), ("推送", "push"), ("销毁", "void"),
        ("能源", "power"), ("电力", "power"), ("容器", "inventory"), ("槽位", "slot"),
    ]
    for a, b in replacements:
        result = result.replace(a, b)
    if not CJK_RE.search(result):
        return preserve_tokens(value, result)

    key = path[-1] if path else "message"
    label = humanize(key)
    # Meaningful generic fallbacks based on key conventions.
    if key.startswith("no-"):
        label = "No " + humanize(key[3:]).lower() + "."
    elif key.startswith("invalid-"):
        label = "Invalid " + humanize(key[8:]).lower() + "."
    elif key.startswith("not-enough-"):
        label = "Not enough " + humanize(key[11:]).lower() + "."
    elif key.startswith("cannot-"):
        label = "Cannot " + humanize(key[7:]).lower() + "."
    elif key.startswith("must-"):
        label = "You must " + humanize(key[5:]).lower() + "."
    elif key.startswith("click-to-"):
        label = "Click to " + humanize(key[9:]).lower() + "."
    elif key.startswith("waiting-for-"):
        label = "Waiting for " + humanize(key[12:]).lower() + "."
    elif key.startswith("error-occurred-"):
        label = "An error occurred while " + humanize(key[15:]).lower() + "."
    elif key.startswith("enabled-"):
        label = humanize(key[8:]) + " enabled."
    elif key.startswith("disabled-"):
        label = humanize(key[9:]) + " disabled."
    elif key.startswith("loaded-"):
        label = humanize(key[7:]) + " loaded."
    elif key.startswith("saving-"):
        label = "Saving " + humanize(key[7:]).lower() + "..."
    elif key.startswith("saved-"):
        label = humanize(key[6:]) + " saved."
    elif key.startswith("set-"):
        label = "Set " + humanize(key[4:]).lower() + "."
    elif key.startswith("toggle-"):
        label = "Toggle " + humanize(key[7:]).lower() + "."
    return preserve_tokens(value, leading_format(value) + label)


def convert(path: tuple[str, ...], value: Any) -> Any:
    dotted = ".".join(path)
    if dotted in MESSAGE_OVERRIDES and not isinstance(MESSAGE_OVERRIDES[dotted], (dict, list)):
        return MESSAGE_OVERRIDES[dotted]
    if isinstance(value, dict):
        return {k: convert(path + (str(k),), v) for k, v in value.items()}
    if isinstance(value, list):
        return [convert(path + (str(i),), v) for i, v in enumerate(value)]
    if not isinstance(value, str):
        return value

    if path and path[0] == "groups":
        if len(path) == 2 and path[1] in GROUPS:
            return GROUPS[path[1]]
        return translate_exact_or_key(path, value)
    if path and path[0] == "theme":
        return THEME.get(path[-1], translate_exact_or_key(path, value))
    if path and path[0] == "items" and len(path) >= 3:
        item_id = path[1]
        field = path[2]
        if field == "name":
            return item_name(item_id)
        if field == "lore":
            # Handled at the item-dict level below.
            return translate_exact_or_key(path, value)
    if path and path[0] == "icons" and path[-1] == "name":
        return humanize(path[1])
    if not CJK_RE.search(value):
        return value
    return translate_exact_or_key(path, value)



FEEDBACK_EN = {
    "afk": "Waiting for activity",
    "already_has_item": "The grabber slot already contains an item",
    "block_already_cut": "The block has already been cut",
    "block_already_pasted": "The block has already been pasted",
    "block_cannot_be_air": "The selected block cannot be air",
    "block_cannot_be_cut": "This block cannot be cut",
    "block_not_match_template": "The block does not match the template",
    "cannot_output_energy": "Network power cannot be supplied to this machine",
    "disabled_blueprint": "This blueprint is disabled",
    "disabled_output": "This recipe output is disabled",
    "error_occurred": "An error occurred",
    "full_energy_buffer": "The energy buffer is full",
    "initialization": "Initializing direction",
    "invalid_block": "Invalid block",
    "invalid_blueprint": "Invalid blueprint",
    "invalid_recipe": "Invalid recipe",
    "invalid_template": "Invalid template",
    "loading_data": "Loading data",
    "no_blueprint_found": "No blueprint inserted",
    "no_blueprint_instance_found": "The blueprint data is invalid",
    "no_direction_set": "No direction configured",
    "no_enough_space": "Not enough space",
    "no_input": "No input items",
    "no_inventory_found": "Inventory not found",
    "no_item_found": "Item not found",
    "no_item_request": "No item was requested",
    "no_linked_block_menu_found": "The linked block has no accessible menu",
    "no_linked_location_found": "No linked block location was configured",
    "no_network_found": "No network connected",
    "no_owner_found": "No owner was found",
    "no_permission": "You do not have permission",
    "no_target_block": "No target block selected",
    "no_template_found": "No template inserted",
    "no_vanilla_recipe_found": "No vanilla recipe found",
    "no_valid_recipe_found": "No valid recipe found",
    "not_allowed_block": "This block is not allowed",
    "not_blueprint": "This item is not a blueprint",
    "not_enough_items_in_network": "Not enough items in the network",
    "not_enough_power": "Not enough network power",
    "not_enough_resources": "Not enough resources in the network",
    "output_full": "The output is full",
    "protected_block": "This block is protected",
    "result_is_too_large": "The result is too large",
    "success": "Success",
    "working": "Working",
    "not_allowed_item": "This item is not allowed",
    "soft_cell_banned": "This operation was limited because the network has too many connected cells",
    "ticking": "Ticking",
    "transfer_ticking": "Transfer ticking",
    "transfer_try_push_item_with_counter": "Attempting to push an item with counter tracking",
    "transfer_try_push_item": "Attempting to push an item",
    "transfer_try_grab_item_with_counter": "Attempting to grab an item with counter tracking",
    "transfer_try_grab_item": "Attempting to grab an item",
    "root_request_0": "Invalid request: attempted to retrieve zero items",
    "root_limiting_access_output": "Network output access was rate-limited",
    "root_limiting_access_input": "Network input access was rate-limited",
    "too_many_entities": "Too many nearby entities",
    "no_target_location": "No target location configured",
    "no_target_network_found": "Target network not found",
    "same_network": "Items cannot be sent to the same network",
    "no_menu": "Menu not found",
    "unsupported_blueprint": "Unsupported blueprint",
    "broken_blueprint": "The blueprint is damaged",
    "no_location_found": "Location not found",
    "no_enough_items": "Not enough items",
    "cannot_access_code": "Unable to access the required code",
}

DATA_SAVING_EN = {
    "saving-drawer": "Saving Network Drawer data...",
    "saved-drawer": "Network Drawer data saved successfully.",
    "error-occurred-when-creating-data-folder": "Unable to create the Networks data folder.",
    "error-occurred-when-saving-new-data": "An error occurred while saving new storage data:",
    "error-occurred-when-updating-environment-var": "An error occurred while updating environment data:",
    "error-occurred-when-loading-data": "An error occurred while loading storage data:",
    "error-occurred-when-saving-itemstack": "An error occurred while saving an item:",
    "error-occurred-when-updating-container-data": "An error occurred while updating container data:",
    "error-occurred-when-updating-storage": "An error occurred while updating storage:",
    "error-occurred-when-fixing-data": "An error occurred while repairing container data:",
    "error-occurred-when-loading-itemstack": "An error occurred while loading an item:",
    "error-occurred-when-loading-environment-var": "An error occurred while loading environment data:",
    "error-occurred-when-loading-storage": "An error occurred while loading stored items:",
    "error-occurred-when-executing-query": "An error occurred while executing a data query:",
}

DEBUG_EN = {
    "info": "%s - %s",
    "viewer-info": "<warning>%s - %s",
    "toggle-debug": "<success>Debug mode for this block is now: %s.",
    "enabled-debug": "<success>Debug mode remains enabled until it is manually disabled or the server stops.",
    "wildchests": "WildChests installed: %s",
    "ischest": "WildChests identifies this block as a chest: %s",
    "wildchests-trigger-success": "WildChests integration test succeeded.",
    "wildchests-trigger-failed": "WildChests integration test failed.",
    "status_view": "Location %s reported: %s",
}

COMMAND_HELP = [
    "&6Networks command help:",
    "&6/networks help - Show this help message.",
    "&6/networks fillquantum <amount> - Set the amount stored in the Quantum Storage held in your hand.",
    "&6/networks fixblueprint <keyInMeta> - Repair the Crafting Blueprint held in your hand.",
    "&6/networks addstorageitem <amount> - Add the held item to the Network Drawer you are looking at.",
    "&6/networks reducestorageitem <amount> - Remove items from the Network Drawer you are looking at.",
    "&6/networks setquantum <amount> - Set the item and amount in the Quantum Storage you are looking at.",
    "&6/networks setcontainerid <containerId> - Set a Network Drawer's container ID.",
    "&6/networks updateitem - Update the item held in your hand.",
    "&6/networks getstorageitem <slot> - Get an item from a slot in the Network Drawer you are looking at.",
    "&6/networks viewlog - View diagnostic output from the AdminDebuggable block you are looking at.",
]

COMMAND_EXAMPLES = {
    "help": ["&6/networks help - Show command help.", "&6Example: /networks help"],
    "fillquantum": ["&6/networks fillQuantum <amount> - Set the held Quantum Storage amount.", "&6Example: /networks fillQuantum 1000"],
    "fixblueprint": ["&6/networks fixBlueprint <keyInMeta> - Repair the held Crafting Blueprint.", "&6Example: /networks fixBlueprint networks-changed"],
    "addstorageitem": ["&6/networks addStorageItem <amount> - Add held items to the targeted Network Drawer.", "&6Example: /networks addStorageItem 1000"],
    "reducestorageitem": ["&6/networks reduceStorageItem <amount> - Remove items from the targeted Network Drawer.", "&6Example: /networks reduceStorageItem 1000"],
    "setquantum": ["&6/networks setQuantum <amount> - Set the targeted Quantum Storage item and amount.", "&6Example: /networks setQuantum 1000"],
    "setcontainerid": ["&6/networks setContainerId <containerId> - Set the targeted Network Drawer's ID.", "&6Example: /networks setContainerId 6"],
    "getstorageitem": ["&6/networks getStorageItem <slot> - Get an item from the targeted Network Drawer.", "&6Example: /networks getStorageItem 0"],
    "updateitem": ["&6/networks updateItem - Update the held item.", "&6Example: /networks updateItem"],
    "viewlog": ["&6/networks viewLog - View diagnostic output from the targeted block.", "&6Example: /networks viewLog"],
    "unknown-command": ["<error>Unknown command. Use /networks help for assistance."],
}

CLICK_BEHAVIOR = {
    "messages.normal-operation.directional.display_lore": [
        "<click_info>Left-click: <passive>Set direction",
        "<click_info>Shift + Left-click: <passive>Open the target block",
    ],
    "messages.normal-operation.manager.quantum-manager-click-behavior": [
        "<click_info>Left-click: <passive>Open storage",
        "<click_info>Left-click while holding an item: <passive>Set the storage item",
        "<click_info>Right-click: <passive>Highlight storage",
        "<click_info>Shift + Left-click: <passive>Pin or unpin storage",
        "<click_info>Shift + Right-click: <passive>Rename storage",
        "<click_info>Shift + Right-click while holding an item: <passive>Set the display icon",
    ],
    "messages.normal-operation.manager.drawer-manager-click-behavior": [
        "<click_info>Left-click: <passive>Open storage",
        "<click_info>Right-click: <passive>Highlight storage",
        "<click_info>Shift + Left-click: <passive>Pin or unpin storage",
        "<click_info>Shift + Right-click: <passive>Rename storage",
        "<click_info>Shift + Right-click while holding an item: <passive>Set the display icon",
    ],
    "messages.normal-operation.manager.crafter-manager-click-behavior": [
        "<click_info>Left-click: <passive>Open the crafter",
        "<click_info>Right-click: <passive>Highlight the crafter",
        "<click_info>Shift + Left-click: <passive>Pin or unpin the crafter",
        "<click_info>Shift + Right-click: <passive>Rename the crafter",
        "<click_info>Q: <passive>Remove this crafter from the manager",
        "<click_info>Shift + Q: <passive>Clear the assigned blueprint",
        "<click_info>Middle-click: <passive>Set the display icon",
    ],
    "messages.normal-operation.viewer.item-flow-viewer-click-behavior": [
        "<click_info>Click: <passive>View item-flow details",
    ],
    "messages.normal-operation.viewer.item-flow-viewer-sub-click-behavior": [
        "<click_info>Click: <passive>Return to the item-flow overview",
    ],
    "messages.normal-operation.viewer.linker-grid-click-behavior": [
        "<click_info>Click: <passive>Open the linked storage",
    ],
}

INTEGRATIONS_EN = {
    "found-slimehud": "SlimeHUD detected; registering Networks HUD support.",
    "not-found-slimehud": "Update SlimeHUD to use Networks HUD support.",
    "found-netheopoiesis": "Netheopoiesis detected; registering integration items.",
    "not-found-netheopoiesis": "Netheopoiesis is required for these integration items.",
}

SUPER_HEAD = [
    "============================================================",
    "Networks Expansion - Slimefun Legacy Edition",
    "Original project by Sefiraat",
    "Expansion development by the NetworksExpansion contributors",
    "Legacy maintenance: https://github.com/wickidcow/SF_NetworksExp",
    "Always stop the server normally to protect stored network data.",
    "============================================================",
]

def generic_icon_lore(icon_key: str) -> list[str]:
    key = icon_key.lower()
    if "next" in key:
        return ["Click to open the next page."]
    if "previous" in key or "prev" in key:
        return ["Click to open the previous page."]
    if "back" in key:
        return ["Click to return to the previous menu."]
    if "search" in key or "filter" in key:
        return ["Click to configure the item search filter."]
    if "sort" in key:
        return ["Click to change the sorting mode."]
    if "status" in key:
        return ["Displays the current Networks status."]
    if "info" in key:
        return ["Displays information about this Networks device."]
    if "input" in key:
        return ["Place input items here."]
    if "output" in key:
        return ["Processed items appear here."]
    if "enabled" in key or "disable" in key or "toggle" in key:
        return ["Click to toggle this setting."]
    return ["Networks interface control."]

def message_fallback(path: tuple[str, ...], source: str) -> str:
    key = path[-1] if path else "message"
    label = humanize(key).replace("True", "Enabled").replace("False", "Disabled")
    prefix = leading_format(source)
    root = path[0] if path else ""
    if root == "unsupported-operation":
        text = label if label.endswith((".", "!")) else label + "."
        return preserve_tokens(source, prefix + text)
    if root == "completed-operation":
        text = label if label.endswith((".", "!")) else label + "."
        return preserve_tokens(source, prefix + text)
    if key.startswith("no_") or key.startswith("no-"):
        text = "No " + humanize(key[3:]).lower() + "."
    elif key.startswith("not_") or key.startswith("not-"):
        text = "Invalid " + humanize(key[4:]).lower() + "."
    elif key.startswith("invalid_") or key.startswith("invalid-"):
        text = "Invalid " + humanize(key[8:]).lower() + "."
    elif key.startswith("cannot_") or key.startswith("cannot-"):
        text = "Cannot " + humanize(key[7:]).lower() + "."
    elif key.startswith("must_") or key.startswith("must-"):
        text = "You must " + humanize(key[5:]).lower() + "."
    elif key.startswith("waiting_for_") or key.startswith("waiting-for-"):
        text = "Waiting for " + humanize(key[12:]).lower() + "."
    elif key.startswith("click_to_") or key.startswith("click-to-"):
        text = "Click to " + humanize(key[9:]).lower() + "."
    elif key.startswith("set_") or key.startswith("set-"):
        text = "Set " + humanize(key[4:]).lower() + "."
    elif key.startswith("stored_") or key.startswith("stored-"):
        text = humanize(key).replace("Stored", "Stored") + ":"
    else:
        text = label
    return preserve_tokens(source, prefix + text)

def rebuild_messages(source_messages: dict[str, Any], out_messages: dict[str, Any]) -> None:
    out_messages["feedback"] = {k: FEEDBACK_EN.get(k, humanize(k)) for k in source_messages.get("feedback", {})}
    out_messages["data-saving"] = {k: DATA_SAVING_EN.get(k, humanize(k)) for k in source_messages.get("data-saving", {})}
    out_messages["debug"] = {k: preserve_tokens(v, DEBUG_EN.get(k, humanize(k))) for k, v in source_messages.get("debug", {}).items()}
    out_messages.setdefault("commands", {})["help"] = COMMAND_HELP
    out_messages["commands"]["example"] = COMMAND_EXAMPLES
    out_messages["super-head"] = SUPER_HEAD
    integrations = out_messages.setdefault("integrations", {})
    integrations.update(INTEGRATIONS_EN)
    integrations["slimehud"] = {"empty_quantum_storage": "&7| Empty"}
    integrations["netheopoiesis"] = {
        "dirt_tips": "(or a higher purification tier)",
        "dirt_level": {"highest": "Nether Grass Block", "high": "Greedy Dirt"},
        "stone_chunk_seed": {"name": "Stone Chunk Seed", "lore": ["When fully grown,", "this plant yields Stone Chunks."]},
        "synthetic_seed": {"name": "Synthetic Seed", "lore": ["This seed has no special effect."]},
        "synthetic_emerald_seed": {"name": "Synthetic Emerald Seed", "lore": ["When fully grown,", "this plant yields Synthetic Emeralds."]},
        "synthetic_diamond_seed": {"name": "Synthetic Diamond Seed", "lore": ["When fully grown,", "this plant yields Synthetic Diamonds."]},
        "fragmented_seed": {"name": "Fragmented Seed", "lore": ["When fully grown,", "this plant yields Synthetic Emerald Shards."]},
    }
    for dotted, lines in CLICK_BEHAVIOR.items():
        keys = dotted.split(".")[1:]
        node = out_messages
        for key in keys[:-1]:
            node = node.setdefault(key, {})
        node[keys[-1]] = lines

    def repair(src: Any, dst: Any, path: tuple[str, ...] = ()) -> Any:
        dotted = "messages." + ".".join(path)
        if path == ("super-head",):
            return SUPER_HEAD
        if dotted in CLICK_BEHAVIOR:
            return CLICK_BEHAVIOR[dotted]
        if isinstance(src, dict):
            result = {} if not isinstance(dst, dict) else dict(dst)
            for k, v in src.items():
                result[k] = repair(v, result.get(k), path + (str(k),))
            return result
        if isinstance(src, list):
            if dotted in CLICK_BEHAVIOR:
                return CLICK_BEHAVIOR[dotted]
            return [repair(v, dst[i] if isinstance(dst, list) and i < len(dst) else None, path + (str(i),)) for i, v in enumerate(src)]
        if not isinstance(src, str):
            return src
        full = "messages." + ".".join(path)
        if full in MESSAGE_OVERRIDES:
            return MESSAGE_OVERRIDES[full]
        if not CJK_RE.search(src):
            return src
        candidate = dst if isinstance(dst, str) else ""
        stripped = re.sub(r"(?:&(?:#[0-9A-Fa-f]{6}|[0-9A-FK-ORa-fk-orx])|<[^>]+>)", "", candidate).strip()
        if not candidate or CJK_RE.search(candidate) or re.fullmatch(r"\d+", stripped):
            return message_fallback(path, src)
        return preserve_tokens(src, candidate)

    repaired = repair(source_messages, out_messages)
    out_messages.clear()
    out_messages.update(repaired)


def main() -> None:
    data = yaml.safe_load(SOURCE.read_text(encoding="utf-8"))
    out = convert((), copy.deepcopy(data))

    # Replace complete item records so descriptions stay coherent.
    for item_id, item in out.get("items", {}).items():
        if not isinstance(item, dict):
            continue
        name = item_name(item_id)
        item["name"] = name
        if item_id in ORIGINAL_ITEMS:
            item["lore"] = ORIGINAL_ITEMS[item_id][1]
        else:
            item["lore"] = generic_item_lore(item_id, name)

    rebuild_messages(data.get("messages", {}), out.setdefault("messages", {}))

    def rebuild_icon(node: Any, path: tuple[str, ...]) -> Any:
        if isinstance(node, dict):
            result = {}
            for key, value in node.items():
                dotted = ".".join(path + (str(key),))
                if dotted == "keybinds.scripts.display.name":
                    result[key] = "&6%s's Keybind Script%s (#%s)"
                elif dotted == "offset-show-icon.lore":
                    result[key] = ["&7Current offset: &e%s"]
                elif key == "name" and isinstance(value, str):
                    result[key] = humanize(path[-1] if path else "Networks")
                elif "lore" in key.lower() and isinstance(value, list):
                    generated = generic_icon_lore("_".join(path))
                    if value and isinstance(value[0], str):
                        generated[0] = preserve_tokens(value[0], generated[0])
                    result[key] = generated
                else:
                    result[key] = rebuild_icon(value, path + (str(key),))
            return result
        if isinstance(node, list):
            return [rebuild_icon(value, path + (str(index),)) for index, value in enumerate(node)]
        if isinstance(node, str) and CJK_RE.search(node):
            return humanize(path[-1] if path else "Networks")
        return node

    out["icons"] = rebuild_icon(out.get("icons", {}), ())

    # Ensure no Chinese can leak into the English-facing locale.
    def clean(path: tuple[str, ...], node: Any) -> Any:
        if isinstance(node, dict):
            return {k: clean(path + (str(k),), v) for k, v in node.items()}
        if isinstance(node, list):
            return [clean(path + (str(i),), v) for i, v in enumerate(node)]
        if isinstance(node, str) and CJK_RE.search(node):
            return preserve_tokens(node, leading_format(node) + humanize(path[-1] if path else "Networks message"))
        return node

    out = clean((), out)
    header = (
        "# English locale for SF_NetworksExp Legacy Edition\n"
        "# Classic item names and descriptions follow Sefiraat's original Blob Builds wording.\n"
        "# Expansion-only content uses maintained English terminology.\n"
    )
    OUTPUT.write_text(header + yaml.safe_dump(out, sort_keys=False, allow_unicode=True, width=120), encoding="utf-8")

    text = OUTPUT.read_text(encoding="utf-8")
    remaining = CJK_RE.findall(text)
    if remaining:
        raise SystemExit(f"English locale still contains {len(remaining)} CJK characters")
    print(f"Generated {OUTPUT.relative_to(ROOT)} with no CJK text")


if __name__ == "__main__":
    main()
