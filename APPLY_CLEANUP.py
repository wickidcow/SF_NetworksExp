#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path.cwd()
if not (root / "build.gradle.kts").is_file() or not (root / "src/main/java").is_dir():
    print("Run this script from the root of the SF_NetworksExp repository.", file=sys.stderr)
    raise SystemExit(2)

deletions = [
    ".github/ISSUE_TEMPLATE/bug-report.yml",
    ".github/ISSUE_TEMPLATE/help-wanted.yml",
    ".github/ISSUE_TEMPLATE/other.yml",
    ".github/ISSUE_TEMPLATE/suggestion.yml",
    "src/main/resources/lang/zh-CN.yml",
]
for relative in deletions:
    path = root / relative
    if path.exists() or path.is_symlink():
        path.unlink()
        print(f"Deleted {relative}")
    else:
        print(f"Already absent: {relative}")

verifier = root / "scripts/verify_legacy_compatibility.py"
if verifier.is_file():
    import subprocess
    subprocess.run([sys.executable, str(verifier)], check=True)
else:
    print("Cleanup complete. The verifier was not found, so it was not run.")
