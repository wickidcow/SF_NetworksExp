#!/usr/bin/env python3
"""Offline compatibility and strict evidence tests for ticker profile comparison."""
import unittest

from compare_slimefun_tick_profiles import ProfileError, compare, parse_profiles

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


if __name__ == "__main__":
    unittest.main()
