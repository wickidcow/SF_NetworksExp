#!/usr/bin/env python3
"""Reject unstable Java class-file headers in every packaged Networks class.

This is a bytecode-header packaging gate, not JVM verification or gameplay proof.
"""
from __future__ import annotations

from pathlib import Path
import struct
import sys
import zipfile

MAGIC = b"\xca\xfe\xba\xbe"
OWNED = (
    "io/github/sefiraat/networks/",
    "com/ytdd9527/networksexpansion/",
    "com/balugaq/netex/",
)
MAX_MAJOR = 65


def verify(jar: Path) -> tuple[int, int]:
    if not jar.is_file():
        raise ValueError(f"JAR not found: {jar}")
    checked = 0
    owned = 0
    seen: set[str] = set()
    with zipfile.ZipFile(jar) as archive:
        for info in sorted(archive.infolist(), key=lambda member: member.filename):
            name = info.filename
            if not name.endswith(".class"):
                continue
            if name in seen:
                raise ValueError(f"{name}: duplicate class member")
            seen.add(name)
            try:
                with archive.open(info) as stream:
                    header = stream.read(8)
            except (OSError, RuntimeError, EOFError, zipfile.BadZipFile) as exc:
                raise ValueError(f"{name}: unreadable class header ({type(exc).__name__})") from exc
            if len(header) != 8 or header[:4] != MAGIC:
                raise ValueError(f"{name}: invalid or truncated class header")
            minor, major = struct.unpack(">HH", header[4:8])
            if not 45 <= major <= MAX_MAJOR:
                raise ValueError(f"{name}: class version {major}.{minor} is outside stable Java 21")
            if major >= 56 and minor != 0:
                reason = "preview" if minor == 65535 else "invalid modern minor"
                raise ValueError(f"{name}: {reason} class version {major}.{minor} cannot ship")
            checked += 1
            owned += name.startswith(OWNED)
    if not owned:
        raise ValueError("no Networks-owned classes were found in the JAR")
    return checked, owned


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: verify_java21_bytecode.py <plugin.jar>", file=sys.stderr)
        return 2
    try:
        checked, owned = verify(Path(sys.argv[1]))
    except (OSError, ValueError, zipfile.BadZipFile) as exc:
        print(f"Java 21 bytecode verification failed: {exc}", file=sys.stderr)
        return 1
    print(f"Java 21 bytecode verification passed for {checked} packaged classes ({owned} Networks-owned).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
