#!/usr/bin/env python3
"""Require storage restart and native Grid ledgers, then run actual IE2 buttons.

Only the newly created disposable directory from the prerequisite is used.
Every JVM uses the same exact candidate plugin JARs. Never accepts a live world.
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


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main() -> None:
    if len(sys.argv) != 7:
        raise SystemExit('Usage: native_ie2_player_probe.py <core.jar> <networks.jar> <ie2.jar> <purpur.jar> <new-dir> <client-deps>')
    core, networks, ie2, server, work, modules = map(lambda arg: Path(arg).resolve(), sys.argv[1:])
    if work.exists():
        raise SystemExit('Existing directories and server worlds are not accepted')
    subprocess.run([sys.executable, str(ROOT / 'scripts/native_grid_player_probe.py'),
                    *map(str, (core, networks, ie2, server, work, modules))], check=True)
    manifest_path = work / 'inputs.json'
    manifest = json.loads(manifest_path.read_text())
    if manifest.get('result') != 'PASS' or not manifest.get('native_player_result', '').startswith('PASS: native-player '):
        raise SystemExit('Native Grid and paired storage prerequisites must pass')
    for source, key in ((core, 'core_sha256'), (networks, 'networks_sha256'), (ie2, 'ie2_sha256'), (server, 'server_sha256')):
        if digest(source) != manifest[key]:
            raise SystemExit(f'Candidate changed after prerequisite: {key}')
    manifest.update(result='IE2_PLAYER_PENDING', ie2_player_result='PENDING')
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
    plugins = work / 'plugins'
    (plugins / 'NativeGridPlayerProbe.jar').rename(plugins / 'NativeGridPlayerProbe.fixture-disabled')
    java_bin = Path(os.environ['JAVA_HOME']) / 'bin'
    java, javac = str(java_bin / 'java'), str(java_bin / 'javac')
    node = shutil.which('node')
    if not node:
        raise SystemExit('The loopback client needs Node')
    classes = work / 'native-ie2-classes'
    classpath = os.pathsep.join(map(str, [core, networks, *sorted((work / 'libraries').rglob('*.jar'))]))
    subprocess.run([javac, '--release', '21', '-cp', classpath, '-d', str(classes),
                    str(ROOT / 'tests/runtime/NativeIE2PlayerProbe.java')], check=True)
    with zipfile.ZipFile(plugins / 'NativeIE2PlayerProbe.jar', 'w') as archive:
        for compiled in sorted(classes.rglob('*.class')):
            archive.write(compiled, compiled.relative_to(classes))
        archive.writestr('plugin.yml', "name: NativeIE2PlayerProbe\nversion: 1.0\n"
                         "main: networksprobe.NativeIE2PlayerProbe\napi-version: '1.21'\n"
                         "depend: [Slimefun, Networks, InfinityExpansion2]\n")
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    properties = work / 'server.properties'
    properties.write_text(properties.read_text() + f'\nserver-ip=127.0.0.1\nserver-port={port}\n')
    ready, result = plugins / 'native-ie2-ready.txt', plugins / 'native-ie2-result.txt'
    ready.unlink(missing_ok=True)
    result.unlink(missing_ok=True)
    env = {**os.environ, 'NETWORKS_NATIVE_IE2': 'disposable',
           'NATIVE_PLAYER_PORT': str(port), 'NATIVE_PLAYER_MODULES': str(modules)}
    with (work / 'native-ie2-console.log').open('w') as log, (work / 'native-ie2-client.log').open('w') as client_log:
        process = subprocess.Popen([java, '-Dnetworks.native.ie2=disposable', '-Dterminal.jline=false',
                                    '-Dterminal.ansi=false', '-Xms256M', '-Xmx1800M', '-jar', str(server), '--nogui'],
                                   cwd=work, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
        bot = None
        try:
            deadline = time.monotonic() + 120
            while not ready.is_file() and process.poll() is None and time.monotonic() < deadline:
                time.sleep(0.25)
            if not ready.is_file() or process.poll() is not None:
                raise RuntimeError('Native IE2 server did not become ready')
            bot = subprocess.Popen([node, str(ROOT / 'tests/runtime/native_ie2_player_client.cjs')],
                                   env=env, stdout=client_log, stderr=subprocess.STDOUT)
            bot.wait(timeout=190)
            if bot.returncode != 0:
                raise RuntimeError(f'IE2 client failed with status {bot.returncode}')
            process.wait(timeout=45)
            report = result.read_text().strip() if result.is_file() else 'Missing server-side result'
            if process.returncode != 0 or not report.startswith('PASS: native-ie2-player '):
                raise RuntimeError('Native IE2 oracle failed: ' + report)
            previous_pids = [manifest.get(key) for key in ('seed_pid', 'verify_pid', 'native_player_pid')]
            if process.pid in previous_pids:
                raise RuntimeError('IE2 button tests require a fourth separate JVM')
            manifest.update(result='PASS', ie2_player_result=report, ie2_player_pid=process.pid,
                            ie2_player_protocol='1.21.11', ie2_player_loopback_only=True,
                            ie2_incident_retry_test='explicit synthetic cache hold; actual client retry clicks')
            manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
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
                shutil.copyfile(result, work / 'native-ie2-result.txt')
            if manifest.get('result') != 'PASS':
                manifest.update(result='FAIL', ie2_player_pid=process.pid,
                                ie2_player_result=result.read_text().strip() if result.is_file() else 'No IE2 player PASS')
                manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
            for name in ('native-ie2-console.log', 'native-ie2-client.log'):
                print('\n--- ' + name + ' ---\n' + '\n'.join((work / name).read_text(errors='replace').splitlines()[-70:]), flush=True)


if __name__ == '__main__':
    main()
