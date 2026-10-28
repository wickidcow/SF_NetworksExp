#!/usr/bin/env python3
"""Compare several actual Slimefun ticker-profiler samples without TPS claims.

Read-only offline helper: no server access, plugin actions, or gameplay changes.
"""
from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
import json
from pathlib import Path
import re
import statistics
import sys

HEADER = "===== Slimefun Performance Profiler ====="
COLOR = re.compile(r"\x1b\[[0-9;]*m|§.")
MILLIS = re.compile(r"^\s*(\d+(?:\.\d+)?)\s*ms(?:\s*@.*)?\s*$", re.I)
INTERVAL = re.compile(r"Ticker interval:\s*(\d+)\s*ticks", re.I)
OLD_INTERVAL = re.compile(r"Ticker runtime:[^\n]*\((\d+)\s*ticks\)", re.I)
CALLBACK_WORK = re.compile(
    r"(?:Total sampled ticker work|Total tick time):\s*(\d+(?:\.\d+)?)\s*ms",
    re.I,
)


class ProfileError(ValueError):
    """A profile is incomplete or the samples cannot be compared fairly."""


@dataclass(frozen=True)
class Sample:
    machines: int
    ticker_ticks: int
    total_ms: float
    average_ms: float
    p95_ms: float
    maximum_ms: float
    all_slimefun_ms: float


def metric(value: str, name: str) -> float:
    found = MILLIS.fullmatch(value.strip())
    if not found:
        raise ProfileError(f"Missing/invalid {name} time: {value!r}")
    return float(found.group(1))


def parse_profiles(content: str, item_id: str = "NTW_AUTO_CRAFTER") -> list[Sample]:
    if not re.fullmatch(r"[A-Z0-9_]+", item_id):
        raise ProfileError("Invalid Slimefun item ID")
    cleaned = COLOR.sub("", content)
    parts = cleaned.split(HEADER)
    blocks = parts[1:] if len(parts) > 1 else [parts[0]]
    item_re = re.compile(
        rf"(?m)^[^\n]*?\b{re.escape(item_id)}\s*-\s*(\d+)x\s*\(([^)\n]+)\)"
    )
    result: list[Sample] = []
    for number, block in enumerate(blocks, start=1):
        matches = item_re.findall(block)
        if len(matches) != 1:
            raise ProfileError(
                f"Sample {number}: expected exactly one {item_id} timing, found {len(matches)}"
            )
        machines = int(matches[0][0])
        if machines < 1:
            raise ProfileError(f"Sample {number}: invalid machine count")
        ticks_match = INTERVAL.search(block) or OLD_INTERVAL.search(block)
        work_match = CALLBACK_WORK.search(block)
        if not ticks_match or not work_match:
            raise ProfileError(f"Sample {number}: ticker interval or total callback work missing")
        ticker_ticks = int(ticks_match.group(1))
        if ticker_ticks < 1:
            raise ProfileError(f"Sample {number}: invalid ticker interval")
        fields: dict[str, float] = {}
        initial: float | None = None
        for component in matches[0][1].split("|"):
            component = component.strip()
            if ":" in component:
                name, value = component.split(":", 1)
                key = {"avg": "average", "average": "average",
                       "total": "total", "p95": "p95", "max": "maximum"}.get(
                           name.strip().lower())
                if key is None or key in fields:
                    raise ProfileError(f"Sample {number}: unknown/duplicate field {name}")
                fields[key] = metric(value, key)
            elif initial is None:
                initial = metric(component, "first")
            else:
                raise ProfileError(f"Sample {number}: duplicate leading timing")
        if initial is None or "p95" not in fields or "maximum" not in fields:
            raise ProfileError(f"Sample {number}: incomplete item timing fields")
        # Both the default total-first and the average-ordered profiler output.
        if "total" in fields:
            average_ms = initial
            total_ms = fields["total"]
        else:
            if "average" not in fields:
                raise ProfileError(f"Sample {number}: average missing")
            total_ms = initial
            average_ms = fields["average"]
        if total_ms < 0 or average_ms < 0 or fields["p95"] > fields["maximum"]:
            raise ProfileError(f"Sample {number}: inconsistent item timing")
        result.append(Sample(
            machines, ticker_ticks, total_ms, average_ms, fields["p95"],
            fields["maximum"], float(work_match.group(1)),
        ))
    return result


def compare(
    before: list[Sample], after: list[Sample], min_samples: int = 3,
    require_count: int | None = None,
) -> dict:
    if min_samples < 1 or len(before) < min_samples or len(after) < min_samples:
        raise ProfileError(
            f"Require at least {min_samples} comparable samples in EACH period "
            f"(got {len(before)} before, {len(after)} after)"
        )
    all_samples = before + after
    counts = {s.machines for s in all_samples}
    intervals = {s.ticker_ticks for s in all_samples}
    if len(counts) != 1 or (require_count is not None and counts != {require_count}):
        raise ProfileError(f"Machine count differs: {sorted(counts)}; expected {require_count}")
    if len(intervals) != 1:
        raise ProfileError(f"Ticker intervals differ: {sorted(intervals)}")
    keys = ("total_ms", "average_ms", "p95_ms", "maximum_ms", "all_slimefun_ms")
    rows = {}
    for key in keys:
        first = statistics.median(getattr(s, key) for s in before)
        second = statistics.median(getattr(s, key) for s in after)
        pct = None if first == 0 else 100 * (second - first) / first
        rows[key] = {
            "before_median_ms": round(first, 5),
            "after_median_ms": round(second, 5),
            "change_percent": None if pct is None else round(pct, 3),
        }
    return {
        "item": "NTW_AUTO_CRAFTER",
        "before_samples": len(before),
        "after_samples": len(after),
        "machine_count": next(iter(counts)),
        "ticker_interval_ticks": next(iter(intervals)),
        "metrics": rows,
        "warning": (
            "These figures are medians of accumulated Slimefun callback work, "
            "NOT full-server MSPT/TPS. Matching counts and ticker intervals do "
            "not prove matched chunk/player/server load or a causal speedup."
        ),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", required=True, type=Path, help="3-5 prior /sf tick top samples")
    parser.add_argument("--after", required=True, type=Path, help="3-5 later /sf tick top samples")
    parser.add_argument("--item", default="NTW_AUTO_CRAFTER")
    parser.add_argument("--min-samples", type=int, default=3)
    parser.add_argument("--require-count", type=int)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args(argv)
    try:
        before = parse_profiles(args.before.read_text(encoding="utf-8"), args.item)
        after = parse_profiles(args.after.read_text(encoding="utf-8"), args.item)
        result = compare(before, after, args.min_samples, args.require_count)
        result["item"] = args.item
    except (OSError, ProfileError, UnicodeError) as exc:
        print(f"Refusing ungrounded profiler comparison: {exc}", file=sys.stderr)
        return 2
    if args.json:
        print(json.dumps(result, indent=2, sort_keys=True))
    else:
        print(f"{args.item}: {result['before_samples']} before / {result['after_samples']} after "
              f"({result['machine_count']} machines; every {result['ticker_interval_ticks']} ticks)")
        for key, row in result["metrics"].items():
            change = ("n/a" if row["change_percent"] is None
                      else f"{row['change_percent']:+.2f}%")
            print(f"  {key}: {row['before_median_ms']:.3f} -> "
                  f"{row['after_median_ms']:.3f} ms ({change})")
        print(result["warning"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
