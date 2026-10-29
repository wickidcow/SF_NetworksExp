#!/usr/bin/env python3
"""Check *real javac output* from the Java-25 Paper 26.3 source-compile lane.

The six retained 26.3 deprecations are compatibility readers/writers for
the supported 1.21.11 runtime floor. Do not silence or remove them without
separate metadata-parity and old-item acceptance. Source grep is not evidence.
"""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re

DIAGNOSTIC = re.compile(
    r"src/main/java/(?P<path>[^\s:]+\.java):(?P<line>\d+): "
    r"warning: \[(?P<kind>deprecation|removal)\] (?P<message>.+)$"
)
DIFFERENTER = "com/ytdd9527/networksexpansion/implementation/machines/manual/ItemDifferenter.java"
MAP_UTIL = "com/balugaq/netex/utils/MapUtil.java"

# Use exact javac messages from the verified 26.3 build 176 API. Do not pin
# source line numbers: harmless edits may shift a known call's physical line.
KNOWN = Counter({
    (DIFFERENTER, "deprecation", "getDamageResistant() in ItemMeta has been deprecated"): 1,
    (DIFFERENTER, "deprecation", "hasColor() in MapMeta has been deprecated"): 2,
    (DIFFERENTER, "deprecation", "getColor() in MapMeta has been deprecated"): 2,
    (MAP_UTIL, "deprecation",
     "setColor(@org.jetbrains.annotations.Nullable Color) in MapMeta has been deprecated"): 1,
})


def verify(log: Path, report: Path | None = None) -> Counter:
    observed: Counter = Counter()
    unparsed: list[str] = []
    for line in log.read_text(encoding="utf-8").splitlines():
        match = DIAGNOSTIC.search(line)
        if match:
            key = (match["path"], match["kind"], match["message"])
            observed[key] += 1
        elif ("src/main/java/" in line and "warning:" in line
              and any(token in line.lower() for token in ("deprecation", "removal"))):
            unparsed.append(line)

    missing = KNOWN - observed
    extra = observed - KNOWN
    passed = not missing and not extra and not unparsed
    def describe(counter: Counter) -> list[dict]:
        return [
            {"path": path, "category": category, "message": message, "count": count}
            for (path, category, message), count in sorted(counter.items())
        ]
    payload = {
        "passed": passed,
        "expected_count": sum(KNOWN.values()),
        "observed_count": sum(observed.values()),
        "known": describe(KNOWN),
        "observed": describe(observed),
        "missing": describe(missing),
        "unexpected": describe(extra),
        "unparsed": unparsed,
    }
    if report is not None:
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if not passed:
        raise AssertionError(
            "Paper 26.3 compiler deprecation contract changed; inspect actual javac output "
            f"(missing={sum(missing.values())}, unexpected={sum(extra.values())}, "
            f"unparsed={len(unparsed)}). Do not suppress historical metadata readers."
        )
    print(f"Paper 26.3 source-compile deprecations: {sum(observed.values())} expected, "
          "zero unreviewed compiler diagnostics.")
    return observed


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("log", type=Path, help="Actual 26.3 javac compilation output")
    parser.add_argument("--report", type=Path, default=None)
    args = parser.parse_args()
    verify(args.log, args.report)


if __name__ == "__main__":
    main()
