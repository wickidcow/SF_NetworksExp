#!/usr/bin/env python3
"""Print the built Slimefun plugin JAR from a checked-out core repository."""

from __future__ import annotations

from pathlib import Path
import sys
import zipfile
import yaml


def is_slimefun_plugin(path: Path) -> bool:
    if path.name.endswith(("-sources.jar", "-javadoc.jar", "-tests.jar")):
        return False
    try:
        with zipfile.ZipFile(path) as archive:
            try:
                plugin = yaml.safe_load(archive.read("plugin.yml")) or {}
            except KeyError:
                return False
    except (OSError, zipfile.BadZipFile, yaml.YAMLError):
        return False
    return plugin.get("name") == "Slimefun" and bool(plugin.get("main"))


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: find_slimefun_jar.py <core-repository>", file=sys.stderr)
        return 2

    root = Path(sys.argv[1]).resolve()
    matches = sorted(
        (path for path in root.rglob("*.jar") if is_slimefun_plugin(path)),
        key=lambda path: (path.stat().st_mtime_ns, path.stat().st_size),
        reverse=True,
    )
    if not matches:
        print(f"No built Slimefun plugin JAR found below {root}", file=sys.stderr)
        return 1

    print(matches[0])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
