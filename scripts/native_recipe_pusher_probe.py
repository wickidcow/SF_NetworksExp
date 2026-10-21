#!/usr/bin/env python3
"""Run actual pusher controls and clean restart after the paired storage gates.

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
    ready, result = plugins / 'native-recipe-ready.txt', plugins / 'native-recipe-result.txt'
    ready.unlink(missing_ok=True)
    result.unlink(missing_ok=True)
    (plugins / 'native-recipe-phase.txt').write_text(phase + '\n')
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    properties = work / 'server.properties'
    properties.write_text(properties.read_text() + f'\nserver-ip=127.0.0.1\nserver-port={port}\n'
                          'online-mode=false\nenforce-secure-profile=false\nspawn-protection=0\n')
    env = {**os.environ, 'NETWORKS_NATIVE_RECIPE': 'disposable', 'NATIVE_RECIPE_PHASE': phase,
           'NATIVE_PLAYER_PORT': str(port), 'NATIVE_PLAYER_MODULES': str(modules)}
    console = work / f'native-recipe-{phase}-console.log'
    client = work / f'native-recipe-{phase}-client.log'
    with console.open('w') as log, client.open('w') as client_log:
        process = subprocess.Popen([java, '-Dnetworks.native.recipe=disposable', '-Dterminal.jline=false',
                                    '-Dterminal.ansi=false', '-Xms256M', '-Xmx1800M', '-jar', str(server), '--nogui'],
                                   cwd=work, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
        bot = None
        try:
            deadline = time.monotonic() + 120
            while not ready.is_file() and process.poll() is None and time.monotonic() < deadline:
                time.sleep(0.25)
            if not ready.is_file() or process.poll() is not None:
                raise RuntimeError(f'Native recipe {phase} server did not become ready')
            bot = subprocess.Popen([node, str(ROOT / 'tests/runtime/native_recipe_pusher_client.cjs')],
                                   env=env, stdout=client_log, stderr=subprocess.STDOUT)
            bot.wait(timeout=230)
            if bot.returncode != 0:
                raise RuntimeError(f'Recipe {phase} client failed with status {bot.returncode}')
            process.wait(timeout=45)
            report = result.read_text().strip() if result.is_file() else 'Missing server-side result'
            if process.returncode != 0 or not report.startswith(f'PASS: native-recipe-{phase} '):
                raise RuntimeError(f'Native recipe {phase} oracle failed: ' + report)
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
            if result.is_file():
                shutil.copyfile(result, work / f'native-recipe-{phase}-result.txt')
            for name in (console, client):
                print('\n--- ' + name.name + ' ---\n' + '\n'.join(name.read_text(errors='replace').splitlines()[-70:]), flush=True)


def main() -> None:
    if len(sys.argv) != 7:
        raise SystemExit('Usage: native_recipe_pusher_probe.py <core.jar> <networks.jar> <ie2.jar> <purpur.jar> <new-dir> <client-deps>')
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
    manifest.update(paired_storage_result='PASS', result='RECIPE_PLAYER_PENDING', recipe_player_result='PENDING',
                    recipe_client_lock_sha256=digest(lock))
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
    plugins = work / 'plugins'
    (plugins / 'PairedIE2StorageProbe.jar').rename(plugins / 'PairedIE2StorageProbe.fixture-disabled')
    java_bin = Path(os.environ['JAVA_HOME']) / 'bin'
    java, javac = str(java_bin / 'java'), str(java_bin / 'javac')
    classes = work / 'native-recipe-classes'
    classpath = os.pathsep.join(map(str, [core, networks, *sorted((work / 'libraries').rglob('*.jar'))]))
    try:
        subprocess.run([javac, '--release', '21', '-cp', classpath, '-d', str(classes),
                        str(ROOT / 'tests/runtime/NativeRecipePusherProbe.java'),
                        str(ROOT / 'tests/runtime/NativeRecipeTransferProbe.java')], check=True)
        with zipfile.ZipFile(plugins / 'NativeRecipePusherProbe.jar', 'w') as archive:
            for compiled in sorted(classes.rglob('*.class')):
                archive.write(compiled, compiled.relative_to(classes))
            archive.writestr('plugin.yml', "name: NativeRecipePusherProbe\nversion: 1.0\n"
                             "main: networksprobe.NativeRecipePusherProbe\napi-version: '1.21'\n"
                             "depend: [Slimefun, Networks, InfinityExpansion2]\n")
        seed_pid, seed_report = run_phase(java, node, server, work, modules, 'seed')
        manifest.update(recipe_seed_pid=seed_pid, recipe_seed_result=seed_report, result='RECIPE_RESTART_PENDING')
        manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
        verify_pid, verify_report = run_phase(java, node, server, work, modules, 'verify')
        pids = [manifest.get('seed_pid'), manifest.get('verify_pid'), seed_pid, verify_pid]
        if len(set(pids)) != 4:
            raise RuntimeError('Storage and recipe restart phases require four separate JVMs')
        manifest.update(recipe_verify_pid=verify_pid, recipe_verify_result=verify_report,
                        recipe_player_result='PASS', result='PASS', recipe_protocol='1.21.11',
                        recipe_loopback_only=True,
                        recipe_scope='Classic/More/Best actual GUI controls, 39-item pickup ledger, real smeltery input buffering/OFF parity, clean restart')
    except BaseException:
        manifest.update(result='FAIL', recipe_player_result='FAIL; inspect phase logs/results')
        raise
    finally:
        manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')


if __name__ == '__main__':
    main()
