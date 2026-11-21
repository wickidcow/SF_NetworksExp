#!/usr/bin/env python3
"""Offline compatibility and strict evidence tests for ticker profile comparison."""
import unittest

from compare_slimefun_tick_profiles import ProfileError, compare, parse_profiles, summarize_hotspots, main
from contextlib import redirect_stdout, redirect_stderr
from io import StringIO
from pathlib import Path
from tempfile import TemporaryDirectory

ITEM = "NTW_AUTO_CRAFTER"


def snapshot(total="32.31", count=400, ticks=10, order="total", old=False):
    row = (f"{total}ms | Average: 0.08ms | P95: 0.37ms | Max: 2.21ms @ world -1300,63,801"
           if order == "total" else
           f"0.08ms | Total: {total}ms | P95: 0.37ms | Max: 2.21ms")
    tick = (f"Ticker runtime: 0.5s ({ticks} ticks)" if old
            else f"Ticker interval: {ticks} ticks (0.5s at 20 TPS)")
    header = "Total tick time:" if old else "Total sampled ticker work:"
    return (f"[00:00 INFO]: ===== Slimefun Performance Profiler =====\n"
            f"[00:00 INFO]: {header} 51.37ms\n"
            f"[00:00 INFO]: {tick}\n"
            f"[00:00 INFO]:   {ITEM} - {count}x ({row})\n")


class TickerProfileComparisonTests(unittest.TestCase):
    def test_reads_old_and_new_total_order(self):
        parsed = parse_profiles(snapshot(old=True) + snapshot(old=False))
        self.assertEqual(2, len(parsed))
        self.assertEqual([400, 400], [s.machines for s in parsed])
        self.assertEqual([10, 10], [s.ticker_ticks for s in parsed])
        self.assertEqual([32.31, 32.31], [s.total_ms for s in parsed])

    def test_reads_average_order_and_minecraft_color_codes(self):
        colored = "\u001b[33m" + snapshot(order="average").replace(
            "Total sampled ticker work", "§6Total sampled ticker work") + "\u001b[0m"
        parsed = parse_profiles(colored)
        self.assertEqual(32.31, parsed[0].total_ms)
        self.assertEqual(0.08, parsed[0].average_ms)

    def test_rejects_ambiguous_double_labelled_total_and_average(self):
        # Both display orders have one leading timing and ONE labelled
        # complement. A second label used to be silently ignored, so a
        # corrupted profile could appear to prove a performance change.
        total_first = snapshot().replace(
            "Average: 0.08ms", "Average: 0.08ms | Total: 32.31ms")
        average_first = snapshot(order="average").replace(
            "Total: 32.31ms", "Total: 32.31ms | Average: 99ms")
        for malformed in (total_first, average_first):
            with self.subTest(malformed=malformed):
                with self.assertRaisesRegex(ProfileError, "ambiguous total/average"):
                    parse_profiles(malformed)

    def test_rejects_unrepresentable_nonfinite_timing_values(self):
        # A profiler token can be syntactically numeric but overflow Python's
        # float to infinity, producing a non-standard JSON benchmark report.
        enormous = "9" * 400
        malformed = (
            snapshot(total=enormous),
            snapshot().replace("Average: 0.08ms", f"Average: {enormous}ms"),
            snapshot().replace("P95: 0.37ms", f"P95: {enormous}ms"),
            snapshot().replace("Total sampled ticker work: 51.37ms",
                               f"Total sampled ticker work: {enormous}ms"),
        )
        for sample in malformed:
            with self.subTest(field=sample[-100:]):
                with self.assertRaisesRegex(ProfileError, "[Nn]on-finite"):
                    parse_profiles(sample)

    def test_medians_compare_400_machines_without_claiming_tps(self):
        before = parse_profiles(snapshot("30") + snapshot("32.31") + snapshot("35"))
        after = parse_profiles(snapshot("24") + snapshot("25") + snapshot("27"))
        report = compare(before, after, require_count=400)
        self.assertEqual(-22.625, report["metrics"]["total_ms"]["change_percent"])
        self.assertEqual(3, report["before_samples"])
        self.assertIn("NOT full-server MSPT/TPS", report["warning"])

    def test_rejects_mismatched_machine_counts(self):
        before = parse_profiles(snapshot() * 3)
        after = parse_profiles(snapshot(count=399) * 3)
        with self.assertRaises(ProfileError):
            compare(before, after, require_count=400)

    def test_rejects_mismatched_ticker_intervals(self):
        before = parse_profiles(snapshot() * 3)
        after = parse_profiles(snapshot(ticks=20) * 3)
        with self.assertRaises(ProfileError):
            compare(before, after)

    def test_refuses_one_baseline_sample_and_missing_or_corrupt_entries(self):
        with self.assertRaises(ProfileError):
            compare(parse_profiles(snapshot()), parse_profiles(snapshot() * 3))
        with self.assertRaises(ProfileError):
            parse_profiles(snapshot().replace("P95: 0.37ms | ", ""))
        with self.assertRaises(ProfileError):
            parse_profiles(snapshot().replace("400x", "0x"))

    def test_bad_p95_and_duplicate_entries_fail_closed(self):
        with self.assertRaises(ProfileError):
            parse_profiles(snapshot().replace("P95: 0.37ms", "P95: 5ms"))
        with self.assertRaises(ProfileError):
            parse_profiles(snapshot() + f"[00:00 INFO]: {ITEM} - 400x "
                          "(32.31ms | Average: 0.08ms | P95: 0.37ms | Max: 2.21ms)")

    def test_zero_baseline_does_not_claim_infinite_percentage(self):
        before = parse_profiles(snapshot("0") * 3)
        after = parse_profiles(snapshot("10") * 3)
        report = compare(before, after)
        self.assertIsNone(report["metrics"]["total_ms"]["change_percent"])


    def test_hotspot_coordinates_are_parsed_but_never_exposed_by_default(self):
        before = parse_profiles(snapshot() * 3)
        after = parse_profiles(snapshot(total="31") * 3)
        self.assertEqual("world -1300,63,801", before[0].hotspot)
        default = compare(before, after, require_count=400)
        self.assertNotIn("hotspots", default)
        self.assertNotIn("-1300", str(default))

    def test_opt_in_hotspot_frequencies_identify_repeated_hottest_block(self):
        old = snapshot() * 2 + snapshot().replace("-1300,63,801", "-1302,63,802")
        new = snapshot().replace("-1300,63,801", "-1302,63,802") * 3
        report = summarize_hotspots(parse_profiles(old), parse_profiles(new))
        self.assertEqual(
            [{"location": "world -1300,63,801", "samples": 2, "share_percent": 66.67},
             {"location": "world -1302,63,802", "samples": 1, "share_percent": 33.33}],
            report["before"])
        self.assertEqual(
            [{"location": "world -1302,63,802", "samples": 3, "share_percent": 100.0}],
            report["after"])
        self.assertIn("NOT a spark CPU trace", report["warning"])

    def test_opt_in_hotspots_refuse_missing_malformed_or_partial_receipts(self):
        valid = parse_profiles(snapshot() * 3)
        no_location = parse_profiles(snapshot().replace(
            " @ world -1300,63,801", "") * 3)
        malformed = parse_profiles(snapshot().replace(
            " @ world -1300,63,801", " @ world unknown-coordinates") * 3)
        for missing in (no_location, malformed):
            self.assertIsNone(missing[0].hotspot)
            with self.assertRaisesRegex(ProfileError, "missing/malformed max location"):
                summarize_hotspots(valid, missing)
        with self.assertRaisesRegex(ProfileError, "positive"):
            summarize_hotspots(valid, valid, limit=0)

    def test_hotspot_summary_is_bounded_without_fabricating_other_locations(self):
        samples = (snapshot().replace("-1300,63,801", "-1301,63,801") +
                   snapshot().replace("-1300,63,801", "-1302,63,801") +
                   snapshot().replace("-1300,63,801", "-1303,63,801"))
        current = parse_profiles(samples)
        report = summarize_hotspots(current, current, limit=2)
        self.assertEqual(2, len(report["before"]))
        self.assertEqual(2, len(report["after"]))
        self.assertEqual(1, report["before"][0]["samples"])

    def test_cli_never_prints_coordinates_without_explicit_opt_in(self):
        with TemporaryDirectory() as temporary:
            before = Path(temporary) / "before.log"
            after = Path(temporary) / "after.log"
            before.write_text(snapshot() * 3, encoding="utf-8")
            after.write_text(snapshot(total="30") * 3, encoding="utf-8")
            for include in (False, True):
                stdout, stderr = StringIO(), StringIO()
                argv = ["--before", str(before), "--after", str(after), "--require-count", "400"]
                if include:
                    argv.append("--include-hotspots")
                with redirect_stdout(stdout), redirect_stderr(stderr):
                    code = main(argv)
                self.assertEqual(0, code, stderr.getvalue())
                self.assertEqual(include, "world -1300,63,801" in stdout.getvalue())


if __name__ == "__main__":
    unittest.main()
