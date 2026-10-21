#!/usr/bin/env python3
"""Run native ItemMover production/PDC receipts and a separate clean-restart JVM."""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PURPUR_SHA = 'ee199511bf0a970ee8459fe3a351703fec37293f00bc2de1c5cb746c7e4acc34'

def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def main() -> None:
    if len(sys.argv) != 6:
        raise SystemExit('Usage: native_item_mover_receipts.py <core.jar> <networks.jar> <purpur.jar> <new-directory> <client-modules-directory>')
    core, networks, server, work, modules = map(lambda p: Path(p).resolve(), sys.argv[1:])
    for jar in (core, networks, server):
        if not zipfile.is_zipfile(jar): raise SystemExit(f'Invalid JAR: {jar}')
    if digest(server) != PURPUR_SHA: raise SystemExit('Pinned Purpur1.21.11 build2568 required')
    revisions = {key: os.environ.get(key, '') for key in ('CORE_HEAD', 'NETWORKS_HEAD')}
    if not all(re.fullmatch('[0-9a-f]{40}', sha) for sha in revisions.values()):
        raise SystemExit('Exact source revisions required')
    if not (modules / 'package-lock.json').is_file(): raise SystemExit('Locked native client dependencies required')
    work.mkdir(parents=True, exist_ok=False)
    java_bin = Path(os.environ['JAVA_HOME']) / 'bin'
    java, javac = str(java_bin / 'java'), str(java_bin / 'javac')
    with zipfile.ZipFile(server) as archive:
        expected, url, filename = archive.read('META-INF/download-context').decode().strip().split('\t')
    if Path(filename).name != filename or not url.startswith('https://piston-data.mojang.com/'):
        raise SystemExit('Unexpected server bootstrap')
    (work / 'cache').mkdir()
    subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error', '--retry', '2', '--max-time', '240',
                    url, '--output', str(work / 'cache' / filename)], check=True)
    if digest(work / 'cache' / filename) != expected: raise SystemExit('Mojang server hash mismatch')
    with (work / 'patch.log').open('w') as log:
        subprocess.run([java, '-Dpaperclip.patchonly=true', '-jar', str(server)], cwd=work,
                       stdout=log, stderr=subprocess.STDOUT, check=True, timeout=120)
    plugins = work / 'plugins'
    (plugins / 'bStats').mkdir(parents=True)
    (plugins / 'bStats/config.yml').write_text('enabled: false\n')
    shutil.copyfile(core, plugins / 'Slimefun.jar')
    shutil.copyfile(networks, plugins / 'Networks.jar')
    classpath = os.pathsep.join(map(str, [core, networks, *sorted((work / 'libraries').rglob('*.jar'))]))
    classes = work / 'probe-classes'
    source = ROOT / 'tests/runtime/NativeItemMoverReceiptProbe.java'
    subprocess.run([javac, '--release', '21', '-cp', classpath, '-d', str(classes), str(source)], check=True)
    with zipfile.ZipFile(plugins / 'NativeItemMoverReceiptProbe.jar', 'w') as archive:
        for compiled in sorted(classes.rglob('*.class')): archive.write(compiled, compiled.relative_to(classes))
        archive.writestr('plugin.yml', "name: NativeItemMoverReceiptProbe\nversion: 1.0\n"
                         "main: itemmoverprobe.NativeItemMoverReceiptProbe\napi-version: '1.21'\ndepend: [Slimefun, Networks]\n")
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0)); port = listener.getsockname()[1]
    (work / 'eula.txt').write_text('eula=true\n')
    (work / 'server.properties').write_text(
        f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nlevel-name=fixture\n'
        'level-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\n')
    version = subprocess.check_output([java, '-version'], stderr=subprocess.STDOUT, text=True)
    client_source = ROOT / 'tests/runtime/native_item_mover_client.cjs'
    manifest = {**revisions, 'core_sha256': digest(core), 'networks_sha256': digest(networks),
                'server_sha256': digest(server), 'probe_java_sha256': digest(source),
                'client_sha256': digest(client_source), 'client_lock_sha256': digest(modules / 'package-lock.json'),
                'java_runtime': '\n'.join(line for line in version.splitlines() if not line.startswith('Picked up ')),
                'result': 'PENDING', 'limits': 'Synthetic seeded balances; native production methods/PDC; explicit injected failures; actual main/off-hand client interactions; native persistence with clean restart, not forced-crash proof'}
    output = work / 'inputs.json'
    output.write_text(json.dumps(manifest, indent=2) + '\n')
    for phase in ('seed', 'verify'):
        result = plugins / 'native-item-mover-result.txt'
        ready = plugins / 'native-item-mover-ready.txt'
        result.unlink(missing_ok=True)
        ready.unlink(missing_ok=True)
        (plugins / 'native-item-mover-phase.txt').write_text(phase + '\n')
        with (work / f'{phase}-console.log').open('w') as log, (work / f'{phase}-client.log').open('w') as client_log:
            process = subprocess.Popen([java, '-Dnetworks.item-mover.probe=disposable', '-Dterminal.jline=false',
                                        '-Dterminal.ansi=false', '-Xms256M', '-Xmx1600M', '-jar', str(server), '--nogui'],
                                       cwd=work, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
            client = None
            try:
                if phase == 'seed':
                    deadline = time.monotonic() + 180
                    while not ready.is_file() and process.poll() is None and time.monotonic() < deadline:
                        time.sleep(0.25)
                    if not ready.is_file() or process.poll() is not None:
                        raise RuntimeError('Native ItemMover server did not become ready')
                    env = {**os.environ, 'ITEM_MOVER_PROBE': 'disposable', 'NATIVE_PLAYER_PORT': str(port),
                           'NATIVE_PLAYER_MODULES': str(modules)}
                    client = subprocess.Popen(['node', str(client_source)], env=env, stdout=client_log, stderr=subprocess.STDOUT)
                    client.wait(timeout=160)
                    if client.returncode != 0: raise RuntimeError('Native ItemMover client failed')
                process.wait(timeout=180)
                report = result.read_text().strip() if result.is_file() else 'No native result'
                if process.returncode != 0 or not report.startswith(f'PASS: {phase} '): raise RuntimeError(report)
                manifest[f'{phase}_pid'] = process.pid
                manifest[f'{phase}_result'] = report
                (work / f'{phase}-result.txt').write_text(report + '\n')
                if phase == 'seed':
                    # Retain the completed first JVM's real persisted state before
                    # restart can change caches or resave any loaded records.
                    shutil.copytree(work / 'data-storage', work / 'seed-persisted-data')
                    manifest['seed_database_files'] = {
                        str(path.relative_to(work / 'data-storage')): {'size': path.stat().st_size, 'sha256': digest(path)}
                        for path in sorted((work / 'data-storage').rglob('*'))
                        if path.is_file() and path.name.endswith(('.db', '-wal', '-shm'))}
                print(report, flush=True)
            except BaseException:
                manifest['result'] = 'FAIL'; output.write_text(json.dumps(manifest, indent=2) + '\n'); raise
            finally:
                if client is not None and client.poll() is None:
                    client.kill(); client.wait()
                if process.poll() is None:
                    try: process.communicate(input=b'stop\n', timeout=30)
                    except subprocess.TimeoutExpired: process.kill(); process.wait()
                print('\n'.join((work / f'{phase}-console.log').read_text(errors='replace').splitlines()[-35:]), flush=True)
                print((work / f'{phase}-client.log').read_text(errors='replace'), flush=True)
    if manifest['seed_pid'] == manifest['verify_pid']: raise SystemExit('Independent JVMs required')
    manifest['result'] = 'PASS'; output.write_text(json.dumps(manifest, indent=2) + '\n')
    print('PASS: native ItemMover receipt and independent-JVM persistence', flush=True)

if __name__ == '__main__': main()
