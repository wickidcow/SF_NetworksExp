#!/usr/bin/env python3
"""Regression tests for the real-javac Paper 26.3 diagnostic contract."""
from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from verify_paper26_3_deprecations import KNOWN, verify

SOURCE = "/home/runner/work/SF_NetworksExp/SF_NetworksExp/src/main/java/"


class Paper263DeprecationContractTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.log = self.root / "javac.log"
        self.report = self.root / "report.json"

    @staticmethod
    def expected_lines():
        return [
            f"{SOURCE}{path}:42: warning: [{category}] {message}"
            for (path, category, message), count in KNOWN.items()
            for _ in range(count)
        ]

    def inspect(self, lines):
        self.log.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return verify(self.log, self.report)

    def test_exact_six_compiler_diagnostics_pass_and_are_reported(self):
        actual = self.inspect(self.expected_lines())
        self.assertEqual(KNOWN, actual)
        report = json.loads(self.report.read_text(encoding="utf-8"))
        self.assertTrue(report["passed"])
        self.assertEqual(6, report["observed_count"])
        self.assertEqual([], report["unexpected"])

    def test_github_annotation_timestamp_prefix_is_accepted(self):
        lines = ["2026-10-29T14:53:27Z ##[warning]" + line for line in self.expected_lines()]
        self.assertEqual(6, sum(self.inspect(lines).values()))

    def test_moved_source_line_numbers_do_not_erase_valid_warning(self):
        lines = [line.replace(":42:", ":470:") for line in self.expected_lines()]
        self.assertEqual(6, sum(self.inspect(lines).values()))

    def test_absent_warning_does_not_allow_a_false_green(self):
        with self.assertRaisesRegex(AssertionError, "missing=1"):
            self.inspect(self.expected_lines()[:-1])
        self.assertFalse(json.loads(self.report.read_text(encoding="utf-8"))["passed"])

    def test_new_warning_in_same_file_is_rejected(self):
        new = self.expected_lines()[0].replace("getDamageResistant()", "getCustomModelData()")
        with self.assertRaisesRegex(AssertionError, "unexpected=1"):
            self.inspect(self.expected_lines() + [new])

    def test_new_warning_in_another_owned_file_is_rejected(self):
        new = self.expected_lines()[0].replace("ItemDifferenter.java", "AnotherMachine.java")
        with self.assertRaisesRegex(AssertionError, "unexpected=1"):
            self.inspect(self.expected_lines() + [new])

    def test_duplicate_warning_beyond_known_count_is_rejected(self):
        with self.assertRaisesRegex(AssertionError, "unexpected=1"):
            self.inspect(self.expected_lines() + self.expected_lines()[:1])

    def test_removal_warning_is_never_mistaken_for_allowed_deprecation(self):
        new = self.expected_lines()[0].replace("[deprecation]", "[removal]")
        with self.assertRaisesRegex(AssertionError, "unexpected=1"):
            self.inspect(self.expected_lines() + [new])

    def test_unparsed_production_deprecation_fails_closed(self):
        new = SOURCE + "other/Test.java:42: warning: [deprecation]"
        with self.assertRaisesRegex(AssertionError, "unparsed=1"):
            self.inspect(self.expected_lines() + [new])

    def test_nonproduction_notes_do_not_get_fabricated_as_owned_diagnostics(self):
        self.assertEqual(6, sum(self.inspect(
            self.expected_lines() + ["Note: Some input files use deprecated APIs.",
                                     "warning: [deprecation] other dependency"]
        ).values()))


if __name__ == "__main__":
    unittest.main()
