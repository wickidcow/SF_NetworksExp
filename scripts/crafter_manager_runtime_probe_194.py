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
    if len(sys.argv) not in (5, 6):
        raise SystemExit("Usage: crafter_manager_runtime_probe.py <core.jar> <networks.jar> "
                         "<server.jar> <new-work-dir> [paper-26.3-build-metadata.json]")
    core, networks, server, work = [Path(arg).resolve() for arg in sys.argv[1:5]]
    version = "1.21.11"
    expected_digest = SERVER_SHA256
    if len(sys.argv) == 6:
        metadata = json.loads(Path(sys.argv[5]).read_text(encoding="utf-8"))
        if metadata.get("minecraft_version") != "26.3" or metadata.get("id") != 194:
            raise SystemExit("Expected the exact Paper 26.3 build 194 metadata")
        expected_digest = metadata["downloads"]["server:default"]["checksums"]["sha256"]
        version = "26.3"
    if digest(server) != expected_digest:
        raise SystemExit(f"Server JAR checksum mismatch for {version}")
    work.mkdir(parents=True, exist_ok=False)
    java_home = Path(os.environ["JAVA_HOME"]) / "bin"
    java, javac = str(java_home / "java"), str(java_home / "javac")
    with zipfile.ZipFile(server) as archive:
        expected, url, name = archive.read("META-INF/download-context").decode().strip().split("\t")
    if Path(name).name != name or not url.startswith("https://piston-data.mojang.com/"):
        raise SystemExit("Unexpected Paperclip download context")
    cache = work / "cache"
    cache.mkdir()
    # Mojang's pinned server JAR is ~56 MB. On busy CI runners a 120-second
    # transfer can time out after most bytes arrive; retries resume the partial
    # transfer, and the SHA-256 check below still verifies every downloaded byte.
    subprocess.run(["curl", "--fail", "--location", "--silent", "--show-error",
                    "--retry", "2", "--retry-all-errors", "--retry-delay", "1",
                    "--continue-at", "-", "--max-time", "240",
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
    subprocess.run([javac, "--release", "25" if version == "26.3" else "21",
                    "-cp", classpath, "-d", str(classes),
                    str(ROOT / "tests/runtime/CrafterManagerDisplayProbe.java"),
                    str(ROOT / "tests/runtime/TropicalFishMetadataProbe.java")], check=True)
    with zipfile.ZipFile(plugins / "NetworksDisplayProbe.jar", "w") as archive:
        for path in sorted(classes.rglob("*.class")):
            if not path.name.startswith("TropicalFishMetadataProbe"):
                archive.write(path, path.relative_to(classes))
        archive.writestr("plugin.yml", 'name: NetworksDisplayProbe\nversion: 1.0\n'
                         'main: networksprobe.CrafterManagerDisplayProbe\napi-version: "1.21"\n'
                         'depend: [Slimefun, Networks]\n')
    with zipfile.ZipFile(plugins / "NetworksMetadataProbe.jar", "w") as archive:
        for path in sorted(classes.rglob("TropicalFishMetadataProbe*.class")):
            archive.write(path, path.relative_to(classes))
        archive.writestr("plugin.yml", 'name: NetworksMetadataProbe\nversion: 1.0\n'
                         'main: networksprobe.TropicalFishMetadataProbe\napi-version: "1.21"\n'
                         'depend: [Slimefun, Networks, NetworksDisplayProbe]\n')
    (work / "eula.txt").write_text("eula=true\n")
    (work / "server.properties").write_text("online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
                                           "level-name=fixture\nlevel-type=minecraft:flat\n"
                                           "generate-structures=false\nview-distance=2\nsimulation-distance=2\n")
    (work / "inputs.json").write_text(json.dumps({
        "minecraft_version": version,
        "core": digest(core),
        "networks": digest(networks),
        "server": digest(server),
        "expected_server_sha256": expected_digest,
    }, indent=2) + "\n")
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
    metadata_result = plugins / "tropical-fish-metadata-result.txt"
    if not metadata_result.is_file():
        result.write_text("FAIL: native tropical-fish metadata probe did not execute\n" + report)
        raise SystemExit("Native tropical-fish metadata probe did not execute")
    metadata_report = metadata_result.read_text()
    print(metadata_report.strip())
    if not metadata_report.startswith("PASS: native tropical-fish metadata comparisons="):
        result.write_text("FAIL: native tropical-fish metadata comparison\n" + metadata_report + "\n" + report)
        raise SystemExit("Native tropical-fish metadata comparison failed")
    # Retain both mandatory results in the artifact already collected by the workflow.
    result.write_text(report.rstrip() + "\n" + metadata_report)


if __name__ == "__main__":
    main()
