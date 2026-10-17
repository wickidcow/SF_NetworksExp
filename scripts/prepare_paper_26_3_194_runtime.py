#!/usr/bin/env python3
"""Select exact Paper 26.3 build 194 from official PaperMC v3 metadata.

The workflow downloads only this build and checks its published SHA-256.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse


# PaperMC's official stable build 194, verified by the successful native 26.3
# staging run. A silent artifact replacement must not change our tested server.
PINNED_PAPER_26_3_SHA256 = "5fac0b6937b7aecea1b5b40e6a992f7b0802e4271413be35a6913d30df960784"


def prepare(builds_file: Path, metadata_file: Path, url_file: Path, sha_file: Path) -> None:
    raw = json.loads(builds_file.read_text(encoding="utf-8"))
    builds = raw if isinstance(raw, list) else raw.get("builds", [])
    if not isinstance(builds, list):
        raise SystemExit("PaperMC metadata did not contain a builds list")

    matching = [build for build in builds
                if isinstance(build, dict) and build.get("id") == 194]
    if len(matching) != 1:
        raise SystemExit("Paper 26.3 build 194 missing; refusing any other build")

    record = matching[0]
    downloads = record.get("downloads", {})
    download = downloads.get("server:default") if isinstance(downloads, dict) else None
    if not isinstance(download, dict):
        raise SystemExit("Missing default Paper 26.3 server download")

    url = download.get("url", "")
    checksum = download.get("checksums", {}).get("sha256", "")
    parsed = urlparse(url)
    host = parsed.hostname
    if parsed.scheme != "https" or not host or (
        host != "papermc.io" and not host.endswith(".papermc.io")
    ):
        raise SystemExit("Untrusted Paper server download domain")
    if not isinstance(checksum, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", checksum):
        raise SystemExit("Missing official Paper 26.3 SHA-256")
    if record.get("channel") != "STABLE":
        raise SystemExit("Paper 26.3 build 194 is not marked STABLE")
    if checksum.lower() != PINNED_PAPER_26_3_SHA256:
        raise SystemExit("Paper 26.3 build 194 checksum changed; refuse unreviewed artifact")

    normalized = {
        "id": 194,
        "minecraft_version": "26.3",
        "channel": record.get("channel", "UNKNOWN"),
        "downloads": {
            "server:default": {
                "url": url,
                "checksums": {"sha256": checksum.lower()},
            }
        },
    }
    metadata_file.write_text(json.dumps(normalized, indent=2) + "\n", encoding="utf-8")
    url_file.write_text(url + "\n", encoding="utf-8")
    sha_file.write_text(checksum.lower() + "\n", encoding="utf-8")
    print(f"Pinned Paper 26.3 build 194 ({normalized['channel']}) SHA-256: {checksum.lower()}")


if __name__ == "__main__":
    if len(sys.argv) != 5:
        raise SystemExit(
            "Usage: prepare_paper_26_3_runtime.py "
            "<builds.json> <normalized.json> <url.txt> <sha.txt>"
        )
    prepare(*map(Path, sys.argv[1:]))
