#!/usr/bin/env python3
"""Run all manager and related viewer display regressions on a fresh, disposable Purpur 1.21.11 server."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SERVER_SHA256 = "ee199511bf0a970ee8459fe3a351703fec37293f00bc2de1c5cb746c7e4acc34"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    if len(sys.argv) != 5:
        raise SystemExit("Usage: crafter_manager_runtime_probe.py <core.jar> <networks.jar> <purpur-2568.jar> <new-work-dir>")
    core, networks, server, work = [Path(arg).resolve() for arg in sys.argv[1:]]
    if digest(server) != SERVER_SHA256:
        raise SystemExit("Expected the pinned Purpur 1.21.11 build 2568 JAR")
    work.mkdir(parents=True, exist_ok=False)
    java_home = Path(os.environ["JAVA_HOME"]) / "bin"
    java, javac = str(java_home / "java"), str(java_home / "javac")
    with zipfile.ZipFile(server) as archive:
        expected, url, name = archive.read("META-INF/download-context").decode().strip().split("\t")
    if Path(name).name != name or not url.startswith("https://piston-data.mojang.com/"):
        raise SystemExit("Unexpected Paperclip download context")
    cache = work / "cache"
    cache.mkdir()
    subprocess.run(["curl", "--fail", "--location", "--silent", "--show-error", "--max-time", "120",
                    url, "--output", str(cache / name)], check=True)
    if digest(cache / name) != expected:
        raise SystemExit("Mojang server checksum mismatch")
    with (work / "patch.log").open("w") as log:
        subprocess.run([java, "-Dpaperclip.patchonly=true", "-jar", str(server)], cwd=work,
                       stdout=log, stderr=subprocess.STDOUT, timeout=120, check=True)
    plugins = work / "plugins"
    (plugins / "bStats").mkdir(parents=True)
    (plugins / "bStats/config.yml").write_text("enabled: false\n")
    shutil.copyfile(core, plugins / "Slimefun.jar")
    shutil.copyfile(networks, plugins / "Networks.jar")
    classes = work / "probe-classes"
    classpath = os.pathsep.join(map(str, [core, networks, *sorted((work / "libraries").rglob("*.jar"))]))
    subprocess.run([javac, "--release", "21", "-cp", classpath, "-d", str(classes),
                    str(ROOT / "tests/runtime/CrafterManagerDisplayProbe.java")], check=True)
    with zipfile.ZipFile(plugins / "NetworksDisplayProbe.jar", "w") as archive:
        for path in sorted(classes.rglob("*.class")):
            archive.write(path, path.relative_to(classes))
        archive.writestr("plugin.yml", 'name: NetworksDisplayProbe\nversion: 1.0\n'
                         'main: networksprobe.CrafterManagerDisplayProbe\napi-version: "1.21"\n'
                         'depend: [Slimefun, Networks]\n')
    (work / "eula.txt").write_text("eula=true\n")
    (work / "server.properties").write_text("online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
                                           "level-name=fixture\nlevel-type=minecraft:flat\n"
                                           "generate-structures=false\nview-distance=2\nsimulation-distance=2\n")
    (work / "inputs.json").write_text(json.dumps({"core": digest(core), "networks": digest(networks),
                                                "purpur": digest(server)}, indent=2) + "\n")
    with (work / "console.log").open("w") as log:
        process = subprocess.Popen([java, "-Dterminal.jline=false", "-Dterminal.ansi=false", "-Xms256M",
                                    "-Xmx1500M", "-jar", str(server), "--nogui"], cwd=work,
                                   stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
        try:
            process.wait(timeout=180)
        except subprocess.TimeoutExpired:
            try:
                process.communicate(input=b"stop\n", timeout=30)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            raise SystemExit("Probe exceeded runtime deadline; inspect console.log")
    result = plugins / "crafter-display-result.txt"
    if process.returncode != 0 or not result.is_file():
        raise SystemExit("Server did not complete the probe; inspect console.log")
    report = result.read_text()
    print(report.strip())
    if not report.startswith("PASS:"):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
