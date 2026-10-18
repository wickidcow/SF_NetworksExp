#!/usr/bin/env python3
"""Add real loopback player clicks after the existing two-JVM paired storage test.

Only creates a new disposable directory. No existing world or remote endpoint
is accepted. A server-side ledger, client exit and saved PASS are all required.
"""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main() -> None:
    if len(sys.argv) != 7:
        raise SystemExit('Usage: native_grid_player_probe.py <core.jar> <networks.jar> <ie2.jar> <purpur.jar> <new-dir> <client-deps>')
    core, networks, ie2, server, work, modules = map(lambda arg: Path(arg).resolve(), sys.argv[1:])
    if work.exists():
        raise SystemExit('An existing directory/world is never accepted')
    lock = modules / 'package-lock.json'
    if not lock.is_file():
        raise SystemExit('Client dependency lockfile must be retained')
    # This existing harness validates source revision pins and the exact server
    # SHA, then requires both conservation and clean-restart tests to pass.
    subprocess.run([sys.executable, str(ROOT / 'scripts/paired_ie2_storage_probe.py'),
                    str(core), str(networks), str(ie2), str(server), str(work)], check=True)
    manifest_path = work / 'inputs.json'
    manifest = json.loads(manifest_path.read_text())
    if manifest.get('result') != 'PASS':
        raise SystemExit('Paired storage prerequisite did not pass')
    java_bin = Path(os.environ['JAVA_HOME']) / 'bin'
    java, javac = str(java_bin / 'java'), str(java_bin / 'javac')
    plugins = work / 'plugins'
    previous_probe = plugins / 'PairedIE2StorageProbe.jar'
    previous_probe.rename(plugins / 'PairedIE2StorageProbe.fixture-disabled')
    classes = work / 'native-grid-classes'
    classpath = os.pathsep.join(map(str, [core, networks, *sorted((work / 'libraries').rglob('*.jar'))]))
    subprocess.run([javac, '--release', '21', '-cp', classpath, '-d', str(classes),
                    str(ROOT / 'tests/runtime/NativeGridPlayerProbe.java')], check=True)
    with zipfile.ZipFile(plugins / 'NativeGridPlayerProbe.jar', 'w') as archive:
        for compiled in sorted(classes.rglob('*.class')):
            archive.write(compiled, compiled.relative_to(classes))
        archive.writestr('plugin.yml', "name: NativeGridPlayerProbe\nversion: 1.0\n"
                         "main: networksprobe.NativeGridPlayerProbe\napi-version: '1.21'\n"
                         "depend: [Slimefun, Networks, InfinityExpansion2]\n")
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    properties = work / 'server.properties'
    properties.write_text(properties.read_text() + f'\nserver-ip=127.0.0.1\nserver-port={port}\n'
                          'online-mode=false\nenforce-secure-profile=false\nspawn-protection=0\n')
    ready, result = plugins / 'native-grid-ready.txt', plugins / 'native-grid-result.txt'
    ready.unlink(missing_ok=True)
    result.unlink(missing_ok=True)
    env = {**os.environ, 'NETWORKS_NATIVE_PLAYER': 'disposable',
           'NATIVE_PLAYER_PORT': str(port), 'NATIVE_PLAYER_MODULES': str(modules)}
    node = shutil.which('node')
    if not node:
        raise SystemExit('Node runtime is required for the local player')
    with (work / 'native-grid-console.log').open('w') as log, (work / 'native-grid-client.log').open('w') as client_log:
        process = subprocess.Popen([java, '-Dnetworks.native.player=disposable', '-Dterminal.jline=false',
                                    '-Dterminal.ansi=false', '-Xms256M', '-Xmx1800M', '-jar', str(server), '--nogui'],
                                   cwd=work, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
        bot = None
        try:
            deadline = time.monotonic() + 120
            while not ready.is_file() and process.poll() is None and time.monotonic() < deadline:
                time.sleep(0.25)
            if not ready.is_file() or process.poll() is not None:
                raise RuntimeError('Native Grid server did not become ready')
            bot = subprocess.Popen([node, str(ROOT / 'tests/runtime/native_grid_player_client.cjs')],
                                   env=env, stdout=client_log, stderr=subprocess.STDOUT)
            bot.wait(timeout=190)
            if bot.returncode != 0:
                raise RuntimeError(f'Client failed with status {bot.returncode}')
            process.wait(timeout=45)
            report = result.read_text().strip() if result.is_file() else 'Missing server-side result'
            if process.returncode != 0 or not report.startswith('PASS: native-player '):
                raise RuntimeError('Native Grid oracle failed: ' + report)
            manifest.update(native_player_result=report, native_player_pid=process.pid,
                            client_version='mineflayer 4.39.0', client_lock_sha256=sha(lock),
                            native_player_protocol='1.21.11', native_player_loopback_only=True)
            manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
            (work / 'native-grid-result.txt').write_text(report + '\n')
            print(report, flush=True)
        finally:
            if bot is not None and bot.poll() is None:
                bot.kill()
                bot.wait()
            if process.poll() is None:
                try:
                    process.communicate(input=b'stop\n', timeout=30)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
            if result.is_file():
                shutil.copyfile(result, work / 'native-grid-result.txt')
            for name in ('native-grid-console.log', 'native-grid-client.log'):
                print('\n--- ' + name + ' ---\n' + '\n'.join((work / name).read_text(errors='replace').splitlines()[-70:]), flush=True)


if __name__ == '__main__':
    main()
