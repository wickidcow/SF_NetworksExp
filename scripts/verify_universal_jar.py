#!/usr/bin/env python3
"""Verify that the release artifact is one clean, core-independent Networks JAR."""

from __future__ import annotations

from pathlib import Path
import sys
import zipfile
import yaml

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
)


def read_yaml(archive: zipfile.ZipFile, name: str):
    try:
        return yaml.safe_load(archive.read(name)) or {}
    except KeyError as exc:
        raise SystemExit(f"required JAR entry is missing: {name}") from exc
    except yaml.YAMLError as exc:
        raise SystemExit(f"invalid YAML in {name}: {exc}") from exc


def main() -> int:
    if len(sys.argv) not in (2, 3):
        print("usage: verify_universal_jar.py <networks.jar> [expected-version]", file=sys.stderr)
        return 2

    jar = Path(sys.argv[1]).resolve()
    expected_version = sys.argv[2] if len(sys.argv) == 3 else None
    if not jar.is_file():
        print(f"JAR not found: {jar}", file=sys.stderr)
        return 1

    try:
        with zipfile.ZipFile(jar) as archive:
            names = set(archive.namelist())
            for required in REQUIRED_ENTRIES:
                if required not in names:
                    raise SystemExit(f"required JAR entry is missing: {required}")

            forbidden = sorted(
                name for name in names
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
            if "alpha4" not in config_version.lower():
                raise SystemExit(f"config.yml is not the Alpha4 configuration: {config_version!r}")

            item_ids = sorted((locale.get("items") or {}).keys())
            if len(item_ids) != 288:
                raise SystemExit(f"expected 288 preserved item IDs, found {len(item_ids)}")

    except zipfile.BadZipFile as exc:
        print(f"invalid JAR/ZIP file: {jar}: {exc}", file=sys.stderr)
        return 1

    print(
        f"Universal JAR verification passed: {jar.name}, 288 item IDs, "
        "no bundled Slimefun or optional-plugin API classes."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
