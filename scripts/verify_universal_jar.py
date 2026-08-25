#!/usr/bin/env python3
"""Verify that the release artifact is one clean, core-independent Networks JAR."""

from __future__ import annotations

from pathlib import Path
import sys
import zipfile
import yaml

ROOT = Path(__file__).resolve().parents[1]
ITEM_ID_BASELINE = ROOT / "compatibility" / "item-ids-2.1.111.txt"

FORBIDDEN_PREFIXES = (
    # Slimefun core families must always be supplied by the server.
    "io/github/thebusybiscuit/slimefun4/",
    "me/mrCookieSlime/",
    "com/xzavier0722/mc/plugin/slimefun4/",
    "city/norain/slimefun4/",
    # Optional plugin APIs must never be shaded into Networks.
    "com/bgsoftware/wildstacker/",
    "dev/rosewood/rosestacker/",
    "io/github/schntgaispock/slimehud/",
    "dev/sefiraat/netheopoiesis/",
)

REQUIRED_ENTRIES = (
    "plugin.yml",
    "config.yml",
    "lang/en-US.yml",
    "io/github/sefiraat/networks/Networks.class",
    "io/github/sefiraat/networks/utils/TransferAudit.class",
    "io/github/sefiraat/networks/integrations/storage/StorageAdapter.class",
    "io/github/sefiraat/networks/integrations/infinityexpansion2/InfinityExpansion2Integration.class",
    "com/ytdd9527/networksexpansion/utils/databases/DrawerRecoveryJournal.class",
)


def read_yaml(archive: zipfile.ZipFile, name: str):
    try:
        return yaml.safe_load(archive.read(name)) or {}
    except KeyError as exc:
        raise SystemExit(f"required JAR entry is missing: {name}") from exc
    except yaml.YAMLError as exc:
        raise SystemExit(f"invalid YAML in {name}: {exc}") from exc


def read_item_id_baseline() -> list[str]:
    if not ITEM_ID_BASELINE.is_file():
        raise SystemExit(f"item-ID baseline is missing: {ITEM_ID_BASELINE.relative_to(ROOT)}")

    baseline = sorted(
        line.strip()
        for line in ITEM_ID_BASELINE.read_text(encoding="utf-8").splitlines()
        if line.strip()
    )
    if not baseline:
        raise SystemExit("item-ID baseline is empty")
    if len(baseline) != len(set(baseline)):
        raise SystemExit("item-ID baseline contains duplicate IDs")
    return baseline


def main() -> int:
    if len(sys.argv) not in (2, 3):
        print("usage: verify_universal_jar.py <networks.jar> [expected-version]", file=sys.stderr)
        return 2

    jar = Path(sys.argv[1]).resolve()
    expected_version = sys.argv[2] if len(sys.argv) == 3 else None
    if not jar.is_file():
        print(f"JAR not found: {jar}", file=sys.stderr)
        return 1

    baseline_ids = read_item_id_baseline()

    try:
        with zipfile.ZipFile(jar) as archive:
            names = set(archive.namelist())

            for required in REQUIRED_ENTRIES:
                if required not in names:
                    raise SystemExit(f"required JAR entry is missing: {required}")

            forbidden = sorted(
                name
                for name in names
                if name.endswith(".class") and name.startswith(FORBIDDEN_PREFIXES)
            )
            if forbidden:
                sample = "\n - ".join(forbidden[:20])
                raise SystemExit(
                    "release JAR bundles Slimefun or optional-plugin API classes:\n - " + sample
                )

            plugin = read_yaml(archive, "plugin.yml")
            config = read_yaml(archive, "config.yml")
            locale = read_yaml(archive, "lang/en-US.yml")

            if plugin.get("name") != "Networks":
                raise SystemExit("plugin.yml name must remain Networks")
            if plugin.get("main") != "io.github.sefiraat.networks.Networks":
                raise SystemExit("plugin.yml main class changed")
            if plugin.get("depend") != ["Slimefun"]:
                raise SystemExit("plugin.yml must depend only on the stable Slimefun plugin name")
            if expected_version and str(plugin.get("version")) != expected_version:
                raise SystemExit(
                    f"plugin.yml version is {plugin.get('version')!r}; expected {expected_version!r}"
                )

            config_version = str(config.get("config-version", ""))
            if config_version.lower() != "2.1.112-legacy-1.0":
                raise SystemExit(
                    f"config.yml is not the 1.0 Legacy configuration: {config_version!r}"
                )

            database = config.get("database") or {}
            startup_backups = database.get("startup-backups") or {}
            if database.get("integrity-check") is not True or database.get("recovery-journal") is not True:
                raise SystemExit("1.0 Legacy database integrity/recovery defaults are missing")
            if startup_backups.get("enabled") is not True or int(startup_backups.get("retained", 0)) < 1:
                raise SystemExit("1.0 Legacy startup database backup defaults are missing")

            item_ids = sorted((locale.get("items") or {}).keys())
            if item_ids != baseline_ids:
                missing = sorted(set(baseline_ids) - set(item_ids))
                unexpected = sorted(set(item_ids) - set(baseline_ids))
                details = [
                    f"item-ID drift detected: expected {len(baseline_ids)}, found {len(item_ids)}"
                ]
                if missing:
                    details.append("missing IDs: " + ", ".join(missing[:20]))
                if unexpected:
                    details.append("unexpected IDs: " + ", ".join(unexpected[:20]))
                raise SystemExit("\n".join(details))

    except zipfile.BadZipFile as exc:
        print(f"invalid JAR/ZIP file: {jar}: {exc}", file=sys.stderr)
        return 1

    print(
        f"Universal JAR verification passed: {jar.name}, {len(baseline_ids)} item IDs, "
        "no bundled Slimefun or optional-plugin API classes."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
