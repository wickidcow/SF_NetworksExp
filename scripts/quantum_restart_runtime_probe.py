#!/usr/bin/env python3
"""Exercise Quantum Storage persistence across two clean Paper/Purpur JVMs.

Runs only in an isolated GitHub Actions world; never reads user/server worlds.
The seed process creates and explicitly saves a Slimefun block + inventory,
then shuts down cleanly. A second process uses the SAME on-disk world and
Slimefun database to verify stored quantity/PDC and serialize the block drop.
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

from crafter_manager_runtime_probe import SERVER_SHA256, digest

ROOT = Path(__file__).resolve().parents[1]
PAPER_26_3_SHA256 = "c17450c3335aabd95e3e65524a4f945e979d7d79ee65a80b002d1a559c6d7bb3"


def check_inputs(server: Path, metadata_path: Path | None) -> str:
    if metadata_path is None:
        if digest(server) != SERVER_SHA256:
            raise SystemExit("Expected exact pinned Purpur 1.21.11 build 2568")
        return "1.21.11"

    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    expected = metadata["downloads"]["server:default"]["checksums"]["sha256"]
    if (metadata.get("minecraft_version"), metadata.get("id")) != ("26.3", 176):
        raise SystemExit("Expected exact Paper 26.3 build 176 metadata")
    if expected.lower() != PAPER_26_3_SHA256 or digest(server) != PAPER_26_3_SHA256:
        raise SystemExit("Paper 26.3 build 176 digest differs from pinned test server")
    return "26.3"


def start_server(java: str, server: Path, work: Path, phase: str) -> int:
    phase_file = work / "plugins/quantum-restart-phase.txt"
    result_file = work / "plugins/quantum-restart-result.txt"
    log_path = work / f"{phase}-console.log"
    phase_file.write_text(phase + "\n", encoding="utf-8")
    result_file.unlink(missing_ok=True)

    with log_path.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(
            [java, "-Dterminal.jline=false", "-Dterminal.ansi=false",
             "-Xms256M", "-Xmx1500M", "-jar", str(server), "--nogui"],
            cwd=work,
            stdin=subprocess.PIPE,
            stdout=log,
            stderr=subprocess.STDOUT,
        )
        pid = process.pid
        try:
            process.wait(timeout=210)
        except subprocess.TimeoutExpired:
            try:
                process.communicate(input=b"stop\n", timeout=30)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            raise SystemExit(f"Quantum restart phase {phase} exceeded runtime deadline")

    if process.returncode != 0 or not result_file.is_file():
        raise SystemExit(
            f"Quantum restart {phase}: server exited {process.returncode} without probe result; "
            f"inspect {log_path}"
        )
    result = result_file.read_text(encoding="utf-8").strip()
    (work / f"{phase}-result.txt").write_text(result + "\n", encoding="utf-8")
    print(f"Quantum restart {phase} JVM pid={pid}: {result}", flush=True)
    if not result.startswith(f"PASS: {phase} "):
        raise SystemExit(f"Quantum restart {phase} failed: {result}; inspect {log_path}")
    return pid


def main() -> None:
    if len(sys.argv) not in (5, 6):
        raise SystemExit(
            "Usage: quantum_restart_runtime_probe.py "
            "<core.jar> <networks.jar> <server.jar> <new-work-dir> "
            "[paper-26.3-build-metadata.json]"
        )
    core, networks, server, work = [Path(p).resolve() for p in sys.argv[1:5]]
    metadata = Path(sys.argv[5]).resolve() if len(sys.argv) == 6 else None
    version = check_inputs(server, metadata)

    work.mkdir(parents=True, exist_ok=False)
    java_home = Path(os.environ["JAVA_HOME"]) / "bin"
    java, javac = str(java_home / "java"), str(java_home / "javac")

    # Paperclip's exact Mojang input, taken from the already-pinned server JAR.
    with zipfile.ZipFile(server) as archive:
        expected, url, name = archive.read("META-INF/download-context").decode().strip().split("\t")
    if Path(name).name != name or not url.startswith("https://piston-data.mojang.com/"):
        raise SystemExit("Unexpected Paperclip download context")
    cache = work / "cache"
    cache.mkdir()
    subprocess.run(
        ["curl", "--fail", "--location", "--silent", "--show-error",
         "--retry", "2", "--retry-all-errors", "--retry-delay", "1",
         "--continue-at", "-", "--max-time", "240",
         url, "--output", str(cache / name)],
        check=True,
    )
    if digest(cache / name) != expected:
        raise SystemExit("Pinned Mojang server input checksum mismatch")
    with (work / "patch.log").open("w", encoding="utf-8") as patch_log:
        subprocess.run(
            [java, "-Dpaperclip.patchonly=true", "-jar", str(server)],
            cwd=work, stdout=patch_log, stderr=subprocess.STDOUT,
            timeout=120, check=True,
        )

    plugins = work / "plugins"
    plugins.mkdir(exist_ok=True)
    (plugins / "bStats").mkdir(exist_ok=True)
    (plugins / "bStats/config.yml").write_text("enabled: false\n", encoding="utf-8")
    shutil.copyfile(core, plugins / "Slimefun.jar")
    shutil.copyfile(networks, plugins / "Networks.jar")

    classes = work / "restart-probe-classes"
    classpath = os.pathsep.join(
        map(str, [core, networks, *sorted((work / "libraries").rglob("*.jar"))])
    )
    subprocess.run(
        [javac, "--release", "25" if version == "26.3" else "21",
         "-cp", classpath, "-d", str(classes),
         str(ROOT / "tests/runtime/QuantumRestartProbe.java")],
        check=True,
    )
    with zipfile.ZipFile(plugins / "QuantumRestartProbe.jar", "w") as archive:
        for path in sorted(classes.rglob("*.class")):
            archive.write(path, path.relative_to(classes))
        archive.writestr(
            "plugin.yml",
            'name: QuantumRestartProbe\nversion: 1.0\n'
            'main: networksprobe.QuantumRestartProbe\napi-version: "1.21"\n'
            'depend: [Slimefun, Networks]\n',
        )

    (work / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (work / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
        "level-name=fixture\nlevel-type=minecraft:flat\n"
        "generate-structures=false\nview-distance=2\nsimulation-distance=2\n",
        encoding="utf-8",
    )
    (work / "inputs.json").write_text(
        json.dumps({
            "minecraft_version": version,
            "core": digest(core),
            "networks": digest(networks),
            "server": digest(server),
            "mojang_input": expected,
            "two_separate_processes": True,
        }, indent=2) + "\n",
        encoding="utf-8",
    )

    seed_pid = start_server(java, server, work, "seed")
    if not (work / "fixture/region").is_dir():
        raise SystemExit("Seed phase did not persist any world region directory")
    verify_pid = start_server(java, server, work, "verify")
    if seed_pid == verify_pid:
        raise SystemExit("Expected independent Java server processes")

    print(
        f"PASS: Quantum Storage persisted data was recovered after full JVM restart "
        f"on {version}; seed pid={seed_pid}, verify pid={verify_pid}",
        flush=True,
    )


if __name__ == "__main__":
    main()
