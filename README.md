<div align="center">

# 🌐📦 Networks — Slimefun Legacy

**Storage networks, remote access, automation, crafting, and high-capacity item movement for Slimefun.**

[![Visual Wiki](https://img.shields.io/badge/Visual%20Wiki-Open%20Documentation-6bd425?style=for-the-badge&logo=github)](https://wickidcow.github.io/SF_NetworksExp/)

![Slimefun Legacy](https://img.shields.io/badge/Slimefun-Legacy-6bd425?style=for-the-badge)
![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge)
![Maintained for AlbionMC.com](https://img.shields.io/badge/Maintained%20for-albionmc.com-7b68ee?style=for-the-badge)

</div>

> [!IMPORTANT]
> Networks Legacy is an **unofficial community-maintained continuation** with Slimefun Legacy as its primary target. It is developed and maintained for use on **albionmc.com**, while preserving the Networks/NetworksExpansion project history and saved-world identity.

## 🌐 What does Networks do?

Networks creates a powerful Slimefun **digital storage and logistics system**. Players can connect storage, move items through network nodes, access inventories through Grids, use Drawers and Quantum Storage, import/export items, automate crafting, and build larger Cargo-style logistics systems.

The maintained fork preserves the Bukkit plugin name `Networks`, existing Slimefun item IDs, persistent-data namespaces, placed machines, and established storage/database locations wherever practical so existing worlds can upgrade without an intentional format reset.

## 🛡️ Slimefun Legacy maintenance

Current stability work includes:

- Slimefun Legacy as the primary/release-blocking target;
- compatibility checks against other Slimefun API-family implementations where useful;
- preservation of hundreds of existing item IDs, recipes, namespaces, and placed blocks;
- transfer rollback and post-deposit compensation diagnostics;
- transactional drawer amount snapshots;
- startup database backups and SQLite integrity checks;
- recovery journaling for critical storage operations;
- controller circuit breakers and bounded Doctor maintenance;
- chunk-lifecycle cleanup and safer node registration;
- optional Infinity Expansion 2 storage-unit integration through a fail-soft adapter;
- `/networks doctor` diagnostics for databases, transfers, controllers, registries, and storage adapters.

Back up the complete server before replacing an established Networks build. Test Controllers, Grids, Drawers, storage units, Importers/Exporters, Pushers/Grabbers, wireless links, blueprints, autocrafters, chunk unload/reload, and a clean restart before moving a new build into production.

Do **not** use `/reload` when changing Slimefun or Networks JARs.

## ❤️ Credits & project lineage

Networks Legacy stands on work from several branches of the Networks family:

- **Sefiraat** — creator of the original **Networks** project and its classic English gameplay/wording.
- **Sefiraat/Networks** — original source repository and root of this fork chain.
- **SlimefunGuguProject contributors** — later Slimefun compatibility and maintenance work used throughout the wider Networks family.
- **ytdd9527/NetworksExpansion** — the **immediate upstream fork** from which `wickidcow/SF_NetworksExp` was created.
- **ytdd9527, balugaq, yitoudaidai, tinalness, and other NetworksExpansion contributors** — important later development and maintenance.
- **Other Networks/NetworksExpansion community forks** — additional fixes and compatibility ideas reviewed during maintenance.
- **wickidcow / Slimefun Legacy** — current preservation, storage-safety, and compatibility maintenance for modern servers and albionmc.com.

This fork intentionally preserves those credits and does not claim ownership of the original Networks design or community work.

## 📜 GNU General Public License v3.0

Networks Legacy is licensed under the **GNU General Public License v3.0 (GPLv3)**. See `LICENSE` for the complete terms.

If you distribute Networks or a modified GPL-covered version, comply with GPLv3, including preserving applicable notices, identifying modified versions, licensing covered modified source under GPLv3, and making the required Corresponding Source available when distributing object code.

The software is supplied **without warranty** as described by GPLv3.

## ⚖️ Independence & trademark notice

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

Networks, Slimefun Legacy, and this maintenance fork are independent community projects. They are not sponsored, endorsed, approved, or operated by Mojang Studios or Microsoft. Minecraft-related names, brands, and assets remain the property of their respective rights holders.

This fork is also not represented as an official release of Sefiraat, the SlimefunGuguProject, ytdd9527, NetworksExpansion contributors, or the original Slimefun team unless explicitly stated by those parties.

---

<div align="center">

**🌐 Connect it. Store it. Craft it. Move it. 📦**

</div>
