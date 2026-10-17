#!/usr/bin/env python3
"""Run real Networks + IE2 candidate JARs in two disposable Purpur JVMs.

No production world is accepted. A new directory and a pinned server hash
are mandatory. This fixture is not a player-click, full-topology or TPS test.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PURPUR_SHA256 = "ee199511bf0a970ee8459fe3a351703fec37293f00bc2de1c5cb746c7e4acc34"


def digest(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def phase(java: str, server: Path, work: Path, name: str) -> int:
    result = work / "plugins/paired-ie2-result.txt"
    result.unlink(missing_ok=True)
    (work / "plugins/paired-ie2-phase.txt").write_text(name + "\n", encoding="utf-8")
    log_path = work / f"{name}-console.log"
    with log_path.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(
            [java, "-Dnetworks.paired.probe=disposable", "-Dterminal.jline=false",
             "-Dterminal.ansi=false", "-Xms256M", "-Xmx1800M", "-jar", str(server), "--nogui"],
            cwd=work, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
        )
        try:
            process.wait(timeout=240)
        except subprocess.TimeoutExpired:
            try:
                process.communicate(input=b"stop\n", timeout=30)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            raise SystemExit(f"Paired {name} exceeded its deadline; inspect {log_path}")
    report = result.read_text(encoding="utf-8").strip() if result.is_file() else "No probe result"
    (work / f"{name}-result.txt").write_text(report + "\n", encoding="utf-8")
    print(f"Paired storage {name}: pid={process.pid} {report}", flush=True)
    if process.returncode != 0 or not report.startswith(f"PASS: {name} "):
        print("\n".join(log_path.read_text(encoding="utf-8", errors="replace").splitlines()[-100:]))
        raise SystemExit(f"Paired storage {name} failed; no success is inferred from startup")
    return process.pid


def main() -> None:
    if len(sys.argv) != 6:
        raise SystemExit("Usage: paired_ie2_storage_probe.py <core.jar> <networks.jar> <ie2.jar> <purpur.jar> <new-directory>")
    core, networks, ie2, server, work = [Path(arg).resolve() for arg in sys.argv[1:]]
    for jar in (core, networks, ie2, server):
        if not jar.is_file() or not zipfile.is_zipfile(jar):
            raise SystemExit(f"Not a readable JAR: {jar}")
    if digest(server) != PURPUR_SHA256:
        raise SystemExit("Only SHA-pinned Purpur 1.21.11 build 2568 is accepted")
    revisions = {name: os.environ.get(name, "") for name in ("NETWORKS_HEAD", "IE2_HEAD", "CORE_HEAD")}
    if not all(re.fullmatch(r"[0-9a-f]{40}", sha) for sha in revisions.values()):
        raise SystemExit("All three exact source revisions must be supplied by the build")
    work.mkdir(parents=True, exist_ok=False)
    java_bin = Path(os.environ["JAVA_HOME"]) / "bin"
    java, javac = str(java_bin / "java"), str(java_bin / "javac")
    with zipfile.ZipFile(server) as archive:
        expected, url, name = archive.read("META-INF/download-context").decode().strip().split("\t")
    if Path(name).name != name or not url.startswith("https://piston-data.mojang.com/"):
        raise SystemExit("Unexpected pinned Paperclip input")
    cache = work / "cache"
    cache.mkdir()
    subprocess.run(
        ["curl", "--fail", "--location", "--silent", "--show-error", "--retry", "2",
         "--retry-all-errors", "--retry-delay", "1", "--continue-at", "-", "--max-time", "240",
         url, "--output", str(cache / name)], check=True,
    )
    if digest(cache / name) != expected:
        raise SystemExit("Mojang input failed SHA verification")
    with (work / "patch.log").open("w", encoding="utf-8") as log:
        subprocess.run([java, "-Dpaperclip.patchonly=true", "-jar", str(server)], cwd=work,
                       stdout=log, stderr=subprocess.STDOUT, timeout=120, check=True)
    plugins = work / "plugins"
    (plugins / "bStats").mkdir(parents=True)
    (plugins / "bStats/config.yml").write_text("enabled: false\n", encoding="utf-8")
    for source, name in ((core, "Slimefun.jar"), (networks, "Networks.jar"), (ie2, "InfinityExpansion2.jar")):
        shutil.copyfile(source, plugins / name)
    classes = work / "probe-classes"
    classpath = os.pathsep.join(map(str, [core, networks, *sorted((work / "libraries").rglob("*.jar"))]))
    subprocess.run([javac, "--release", "21", "-cp", classpath, "-d", str(classes),
                    str(ROOT / "tests/runtime/PairedIE2StorageProbe.java")], check=True)
    with zipfile.ZipFile(plugins / "PairedIE2StorageProbe.jar", "w") as archive:
        for compiled in sorted(classes.rglob("*.class")):
            archive.write(compiled, compiled.relative_to(classes))
        archive.writestr("plugin.yml", "name: PairedIE2StorageProbe\nversion: 1.0\n"
                         "main: networksprobe.PairedIE2StorageProbe\napi-version: '1.21'\n"
                         "depend: [Slimefun, Networks, InfinityExpansion2]\n")
    (work / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (work / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nlevel-name=fixture\n"
        "level-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\n",
        encoding="utf-8",
    )
    manifest = {**revisions, "core_sha256": digest(core), "networks_sha256": digest(networks),
                "ie2_sha256": digest(ie2), "server_sha256": digest(server), "mojang_sha256": expected,
                "server": "Purpur 1.21.11 build 2568", "separate_jvms": True,
                "fixture_routing": "real root with explicit IE2 barrel set; not full topology discovery"}
    manifest_file = work / "inputs.json"
    manifest_file.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    seed_pid = phase(java, server, work, "seed")
    verify_pid = phase(java, server, work, "verify")
    if seed_pid == verify_pid:
        raise SystemExit("Independent JVM identifiers were expected")
    manifest.update(seed_pid=seed_pid, verify_pid=verify_pid, result="PASS")
    manifest_file.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print("PASS: paired Networks/IE2 storage conservation and two-JVM reload", flush=True)


if __name__ == "__main__":
    main()
