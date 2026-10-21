#!/usr/bin/env python3
"""Reuse the established disposable Dust Extractor runner without modifying production JARs."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'scripts/crafter_manager_runtime_probe.py').read_text(encoding='utf-8')

def replace_once(old: str, new: str) -> None:
    global source
    if source.count(old) != 1:
        raise SystemExit(f'Pinned runner changed: expected one occurrence of {old!r}')
    source = source.replace(old, new, 1)

replace_once('    version = "1.21.11"', '    infinity = Path(os.environ["INFINITY_JAR"]).resolve()\n    version = "1.21.11"')
replace_once('    shutil.copyfile(networks, plugins / "Networks.jar")',
             '    shutil.copyfile(networks, plugins / "Networks.jar")\n    shutil.copyfile(infinity, plugins / "InfinityExpansion2.jar")')
replace_once('tests/runtime/CrafterManagerDisplayProbe.java', 'tests/runtime/InfinityDustTransferProbe.java')
source = source.replace('NetworksDisplayProbe', 'InfinityDustTransferProbe')
replace_once('networksprobe.CrafterManagerDisplayProbe', 'networksprobe.InfinityDustTransferProbe')
replace_once('depend: [Slimefun, Networks]', 'depend: [Slimefun, Networks, InfinityExpansion2]')
source = source.replace('crafter-display-result.txt', 'infinity-dust-result.txt')
replace_once('        "core": digest(core),',
             '        "core": digest(core),\n'
             '        "infinity_expansion_2": digest(infinity),\n'
             '        "candidate_source": os.environ["NETWORKS_HEAD"],\n'
             '        "infinity_source": os.environ["IE2_HEAD"],\n'
             '        "core_source": os.environ["CORE_HEAD"],\n'
             '        "scope": "Final coordinated pair; actual IE2 ticker/process/menu and real Networks line caller; controlled sink; no live server",')
output = ROOT / 'scripts/infinity_dust_runtime_probe.py'
compile(source, str(output), 'exec')
output.write_text(source, encoding='utf-8')
print('Prepared isolated Infinity Dust Extractor runner; production JARs unchanged')

