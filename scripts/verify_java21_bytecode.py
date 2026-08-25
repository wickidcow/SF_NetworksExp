#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import struct
import sys
import zipfile

if len(sys.argv) != 2:
    raise SystemExit("usage: verify_java21_bytecode.py <plugin.jar>")
jar = Path(sys.argv[1])
if not jar.is_file():
    raise SystemExit(f"JAR not found: {jar}")
checked = 0
with zipfile.ZipFile(jar) as archive:
    for name in archive.namelist():
        if not name.endswith(".class") or not (
            name.startswith("io/github/sefiraat/networks/")
            or name.startswith("com/ytdd9527/networksexpansion/")
            or name.startswith("com/balugaq/netex/")
        ):
            continue
        data = archive.read(name)[:8]
        magic, minor, major = struct.unpack(">IHH", data)
        if magic != 0xCAFEBABE:
            raise SystemExit(f"invalid class file: {name}")
        if major > 65:
            raise SystemExit(f"{name} targets class version {major}; Java 21 maximum is 65")
        checked += 1
if checked == 0:
    raise SystemExit("no Networks-owned classes were found in the JAR")
print(f"Java 21 bytecode verification passed for {checked} Networks-owned classes.")
