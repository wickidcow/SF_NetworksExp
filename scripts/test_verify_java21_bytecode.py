"""Exercise the real Networks Java 21 bytecode gate with synthetic JAR headers."""
from __future__ import annotations

import hashlib
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import warnings
import zipfile

SCRIPT = Path(__file__).with_name("verify_java21_bytecode.py")
OWNED = "io/github/sefiraat/networks/Networks.class"


def header(major: int = 65, minor: int = 0, magic: bytes = b"\xca\xfe\xba\xbe") -> bytes:
    return magic + struct.pack(">HH", minor, major) + b"synthetic-header-only"


class Java21BytecodeGateTests(unittest.TestCase):
    def run_gate(self, members: list[tuple[str, bytes]], expected: int) -> str:
        with tempfile.TemporaryDirectory() as temp:
            jar = Path(temp) / "candidate.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                for name, data in members:
                    with warnings.catch_warnings():
                        warnings.simplefilter("ignore", UserWarning)
                        archive.writestr(name, data)
            before = hashlib.sha256(jar.read_bytes()).hexdigest()
            proc = subprocess.run(
                [sys.executable, str(SCRIPT), str(jar)],
                capture_output=True, text=True, check=False, timeout=15,
            )
            self.assertEqual(before, hashlib.sha256(jar.read_bytes()).hexdigest())
            self.assertEqual(expected, proc.returncode, proc.stdout + proc.stderr)
            return proc.stdout + proc.stderr

    def test_java21_and_shaded_java17_pass(self):
        output = self.run_gate([(OWNED, header()), ("shaded/Compat.class", header(61))], 0)
        self.assertIn("2 packaged classes (1 Networks-owned)", output)

    def test_other_owned_namespaces_count(self):
        for name in ("com/ytdd9527/networksexpansion/Drawer.class",
                     "com/balugaq/netex/Bridge.class"):
            with self.subTest(name=name):
                self.assertIn("passed", self.run_gate([(name, header())], 0))

    def test_historical_minor_is_accepted(self):
        self.run_gate([(OWNED, header(55, 65535))], 0)

    def test_newer_owned_major_fails(self):
        self.assertIn("66.0", self.run_gate([(OWNED, header(66))], 1))

    def test_newer_shaded_major_fails(self):
        self.assertIn("shaded/TooNew.class",
                      self.run_gate([(OWNED, header()), ("shaded/TooNew.class", header(69))], 1))

    def test_java21_preview_fails(self):
        self.assertIn("preview", self.run_gate([(OWNED, header(65, 65535))], 1))

    def test_earlier_preview_fails_even_with_stable_owned_class(self):
        self.assertIn("61.65535",
                      self.run_gate([(OWNED, header()), ("shaded/Preview.class", header(61, 65535))], 1))

    def test_invalid_modern_minor_fails(self):
        self.assertIn("invalid modern minor", self.run_gate([(OWNED, header(65, 1))], 1))

    def test_invalid_magic_and_truncated_header_fail(self):
        self.assertIn("invalid", self.run_gate([(OWNED, header(magic=b"FAIL"))], 1))
        self.assertIn("truncated", self.run_gate([(OWNED, header()[:7])], 1))

    def test_duplicate_class_members_fail(self):
        self.assertIn("duplicate", self.run_gate([(OWNED, header()), (OWNED, header())], 1))

    def test_missing_owned_class_fails(self):
        self.assertIn("no Networks-owned", self.run_gate([("external/Only.class", header())], 1))

    def test_invalid_historical_major_fails(self):
        self.assertIn("44.0", self.run_gate([(OWNED, header(44))], 1))

    def test_bad_zip_fails_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            jar = Path(temp) / "bad.jar"
            jar.write_bytes(b"not a jar")
            proc = subprocess.run([sys.executable, str(SCRIPT), str(jar)],
                                  capture_output=True, text=True, check=False, timeout=15)
            self.assertEqual(1, proc.returncode)
            self.assertIn("failed", proc.stderr)


if __name__ == "__main__":
    unittest.main()
