#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent
if not (ROOT / "build.gradle.kts").is_file() or not (ROOT / "src/main/java").is_dir():
    print("Extract this ZIP over the root of the SF_NetworksExp repository first.", file=sys.stderr)
    raise SystemExit(2)

DELETIONS = ['.github/ISSUE_TEMPLATE/bug-report.yml', '.github/ISSUE_TEMPLATE/help-wanted.yml', '.github/ISSUE_TEMPLATE/other.yml', '.github/ISSUE_TEMPLATE/suggestion.yml', 'src/main/resources/lang/zh-CN.yml']
for relative in DELETIONS:
    path = ROOT / relative
    if path.is_file() or path.is_symlink():
        path.unlink()
        print(f"Deleted {relative}")

instructions = ROOT / "DROP_IN_INSTRUCTIONS.txt"
if instructions.exists():
    instructions.unlink()

print("Networks Legacy Alpha1 overlay applied successfully.")
try:
    Path(__file__).resolve().unlink()
except OSError:
    pass
