#!/usr/bin/env python3
"""Run actual Drawer/Quantum controls and clean restart after the paired storage gates.

Uses only a fresh directory, loopback client and the same hashed plugin JARs.
This additional fixture does not replace the separate Grid/IE2 player gates.
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


def run_phase(java: str, node: str, server: Path, work: Path, modules: Path, phase: str) -> tuple[int, str]:
    plugins = work / 'plugins'
    ready, result = plugins / 'native-drawer-ready.txt', plugins / 'native-drawer-result.txt'
    ready.unlink(missing_ok=True)
    result.unlink(missing_ok=True)
    (plugins / 'native-drawer-phase.txt').write_text(phase + '\n')
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    properties = work / 'server.properties'
    properties.write_text(properties.read_text() + f'\nserver-ip=127.0.0.1\nserver-port={port}\n'
                          'online-mode=false\nenforce-secure-profile=false\nspawn-protection=0\n')
    env = {**os.environ, 'NETWORKS_NATIVE_DRAWER': 'disposable', 'NATIVE_DRAWER_PHASE': phase,
           'NATIVE_PLAYER_PORT': str(port), 'NATIVE_PLAYER_MODULES': str(modules)}
    console = work / f'native-drawer-{phase}-console.log'
    client = work / f'native-drawer-{phase}-client.log'
    with console.open('w') as log, client.open('w') as client_log:
        process = subprocess.Popen([java, '-Dnetworks.native.drawer=disposable', '-Dterminal.jline=false',
                                    '-Dterminal.ansi=false', '-Xms256M', '-Xmx1800M', '-jar', str(server), '--nogui'],
                                   cwd=work, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
        bot = None
        try:
            deadline = time.monotonic() + 120
            expected_ready = f'READY: native drawer {phase}'
            observed_ready = ''
            while process.poll() is None and time.monotonic() < deadline:
                try:
                    observed_ready = ready.read_text().strip()
                except FileNotFoundError:
                    observed_ready = ''
                if observed_ready == expected_ready:
                    break
                time.sleep(0.25)
            if observed_ready != expected_ready or process.poll() is not None:
                raise RuntimeError(f'Native Drawer {phase} server did not become ready')
            bot = subprocess.Popen([node, str(ROOT / 'tests/runtime/native_drawer_player_client.cjs')],
                                   env=env, stdout=client_log, stderr=subprocess.STDOUT)
            bot.wait(timeout=230)
            if bot.returncode != 0:
                raise RuntimeError(f'Drawer {phase} client failed with status {bot.returncode}')
            process.wait(timeout=45)
            report = result.read_text().strip() if result.is_file() else 'Missing server-side result'
            if process.returncode != 0 or not report.startswith(f'PASS: native-drawer-{phase} '):
                raise RuntimeError(f'Native Drawer {phase} oracle failed: ' + report)
            print(report, flush=True)
            return process.pid, report
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
            # Retain the exact stopped files before another JVM can open them.
            # These copies are evidence only; the harness never checkpoints,
            # rewrites or restores the live databases/player inventory.
            stopped_storage = work / f'native-drawer-{phase}-stopped-storage'
            for relative in ('data-storage', 'plugins/Networks', 'fixture/playerdata'):
                source = work / relative
                if source.is_dir():
                    shutil.copytree(source, stopped_storage / relative)
            hashes = {
                str(path.relative_to(stopped_storage)): digest(path)
                for path in sorted(stopped_storage.rglob('*')) if path.is_file()
            }
            (work / f'native-drawer-{phase}-storage-hashes.json').write_text(json.dumps(hashes, indent=2) + '\n')
            if result.is_file():
                shutil.copyfile(result, work / f'native-drawer-{phase}-result.txt')
            for name in (console, client):
                print('\n--- ' + name.name + ' ---\n' + '\n'.join(name.read_text(errors='replace').splitlines()[-70:]), flush=True)


def main() -> None:
    if len(sys.argv) != 7:
        raise SystemExit('Usage: native_drawer_player_probe.py <core.jar> <networks.jar> <ie2.jar> <purpur.jar> <new-dir> <client-deps>')
    core, networks, ie2, server, work, modules = map(lambda arg: Path(arg).resolve(), sys.argv[1:])
    if work.exists():
        raise SystemExit('Existing directories and server worlds are not accepted')
    lock = modules / 'package-lock.json'
    if not lock.is_file():
        raise SystemExit('Retained client dependency lockfile is required')
    node = shutil.which('node')
    if not node:
        raise SystemExit('The loopback client needs Node')
    subprocess.run([sys.executable, str(ROOT / 'scripts/paired_ie2_storage_probe.py'),
                    *map(str, (core, networks, ie2, server, work))], check=True)
    manifest_path = work / 'inputs.json'
    manifest = json.loads(manifest_path.read_text())
    if manifest.get('result') != 'PASS':
        raise SystemExit('Paired native storage/restart prerequisites must pass')
    for source, key in ((core, 'core_sha256'), (networks, 'networks_sha256'), (ie2, 'ie2_sha256'), (server, 'server_sha256')):
        if digest(source) != manifest[key]:
            raise SystemExit(f'Candidate changed after prerequisite: {key}')
    manifest.update(paired_storage_result='PASS', result='DRAWER_PLAYER_PENDING', drawer_player_result='PENDING',
                    drawer_client_lock_sha256=digest(lock),
                    drawer_probe_sha256=digest(ROOT / 'tests/runtime/NativeDrawerPlayerProbe.java'),
                    drawer_client_sha256=digest(ROOT / 'tests/runtime/native_drawer_player_client.cjs'),
                    drawer_runner_sha256=digest(Path(__file__)))
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
    plugins = work / 'plugins'
    (plugins / 'PairedIE2StorageProbe.jar').rename(plugins / 'PairedIE2StorageProbe.fixture-disabled')
    java_bin = Path(os.environ['JAVA_HOME']) / 'bin'
    java, javac = str(java_bin / 'java'), str(java_bin / 'javac')
    classes = work / 'native-drawer-classes'
    classpath = os.pathsep.join(map(str, [core, networks, *sorted((work / 'libraries').rglob('*.jar'))]))
    try:
        subprocess.run([javac, '--release', '21', '-cp', classpath, '-d', str(classes),
                        str(ROOT / 'tests/runtime/NativeDrawerPlayerProbe.java')], check=True)
        with zipfile.ZipFile(plugins / 'NativeDrawerPlayerProbe.jar', 'w') as archive:
            for compiled in sorted(classes.rglob('*.class')):
                archive.write(compiled, compiled.relative_to(classes))
            archive.writestr('plugin.yml', "name: NativeDrawerPlayerProbe\nversion: 1.0\n"
                             "main: networksprobe.NativeDrawerPlayerProbe\napi-version: '1.21'\n"
                             "depend: [Slimefun, Networks, InfinityExpansion2]\n")
        seed_pid, seed_report = run_phase(java, node, server, work, modules, 'seed')
        manifest.update(drawer_seed_pid=seed_pid, drawer_seed_result=seed_report, result='DRAWER_RESTART_PENDING')
        manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
        verify_pid, verify_report = run_phase(java, node, server, work, modules, 'verify')
        pids = [manifest.get('seed_pid'), manifest.get('verify_pid'), seed_pid, verify_pid]
        if len(set(pids)) != 4:
            raise RuntimeError('Storage and Drawer restart phases require four separate JVMs')
        manifest.update(drawer_verify_pid=verify_pid, drawer_verify_result=verify_report,
                        drawer_player_result='PASS', result='PASS', drawer_protocol='1.21.11',
                        drawer_loopback_only=True,
                        drawer_scope='Actual Drawer partial/full inventory, cursor/bulk deposits, Quantum source/destination 3000000086-item ledger, exact PDC and clean restart')
    except BaseException:
        manifest.update(result='FAIL', drawer_player_result='FAIL; inspect phase logs/results')
        raise
    finally:
        for phase in ('seed', 'verify'):
            receipt = work / f'native-drawer-{phase}-storage-hashes.json'
            if receipt.is_file():
                manifest[f'drawer_{phase}_stopped_storage_sha256'] = json.loads(receipt.read_text())
        manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')


if __name__ == '__main__':
    main()
