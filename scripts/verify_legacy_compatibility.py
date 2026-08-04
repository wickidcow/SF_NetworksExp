#!/usr/bin/env python3
from __future__ import annotations

from collections import Counter
from pathlib import Path
import re
import sys
import yaml

ROOT = Path(__file__).resolve().parents[1]
CJK = re.compile(r"[\u3400-\u9fff]")
TOKEN = re.compile(r"%\d*\$?[a-zA-Z]|%s|\{\d+\}")
ERRORS: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


def read(path: str) -> str:
    file = ROOT / path
    require(file.is_file(), f"missing required file: {path}")
    return file.read_text(encoding="utf-8") if file.is_file() else ""


def token_counter(node) -> Counter:
    counter = Counter()
    if isinstance(node, dict):
        for value in node.values():
            counter.update(token_counter(value))
    elif isinstance(node, list):
        for value in node:
            counter.update(token_counter(value))
    elif isinstance(node, str):
        counter.update(TOKEN.findall(node))
    return counter


def verify_locale_structure(source, translated, path=()):
    label = ".".join(map(str, path)) or "<root>"
    if isinstance(source, dict):
        if not isinstance(translated, dict):
            ERRORS.append(f"English locale type mismatch at {label}")
            return
        for key, value in source.items():
            if key not in translated:
                ERRORS.append(f"missing English locale path: {label}.{key}")
                continue
            verify_locale_structure(value, translated[key], path + (key,))
    elif isinstance(source, list):
        if not isinstance(translated, list) or not translated:
            ERRORS.append(f"English locale list missing at {label}")
            return
        if token_counter(source) != token_counter(translated):
            ERRORS.append(f"placeholder mismatch in list at {label}")
    elif isinstance(source, str):
        if not isinstance(translated, str):
            ERRORS.append(f"English locale string missing at {label}")
        elif Counter(TOKEN.findall(source)) != Counter(TOKEN.findall(translated)):
            ERRORS.append(f"placeholder mismatch at {label}")


build = read("build.gradle.kts")
plugin = yaml.safe_load(read("src/main/resources/plugin.yml")) or {}
config = yaml.safe_load(read("src/main/resources/config.yml")) or {}
locale_text = read("src/main/resources/lang/en-US.yml")
locale = yaml.safe_load(locale_text) or {}
source_locale = yaml.safe_load(read("scripts/localization/zh-CN-source.yml")) or {}
baseline_ids = [line for line in read("compatibility/item-ids-2.1.111.txt").splitlines() if line]
workflow = read(".github/workflows/build.yml")
wrapper = read("gradle/wrapper/gradle-wrapper.properties")
networks_java = read("src/main/java/io/github/sefiraat/networks/Networks.java")
java_sources = ''.join(p.read_text(encoding="utf-8") for p in (ROOT / "src/main/java").rglob("*.java"))

require(plugin.get("name") == "Networks", "plugin name must remain Networks")
require(plugin.get("main") == "io.github.sefiraat.networks.Networks", "main class changed")
require(plugin.get("depend") == ["Slimefun"], "plugin must depend on Slimefun by its stable plugin name")
require(str(plugin.get("api-version")) == "1.21", "api-version must remain 1.21")
require(config.get("language") == "en-US", "default language must be en-US")
require(config.get("auto-update") is False, "automatic JAR replacement must remain disabled")
require('version = "2.1.112-Legacy-Alpha1"' in build, "project version is not Alpha1")
require('options.release.set(21)' in build, "Java 21 release target is missing")
require('paper-api:1.21.11-R0.1-SNAPSHOT' in build, "Paper 1.21.11 API baseline is missing")
require('compileOnly(files(slimefunLegacyJar))' in build, "exact local Slimefun Legacy dependency is missing")
require('com.github.SlimefunGuguProject:Slimefun4:' not in build, "Gugu Slimefun core dependency is still present")
require('GuizhanLibPlugin' not in build, "GuizhanLibPlugin build dependency is still present")
require('DEFAULT_LANGUAGE = "en-US"' in networks_java, "Networks default language is not en-US")
require('GuizhanUpdater' not in networks_java, "automatic Guizhan updater code is still present")
require('PinyinHelper' not in java_sources, "Pinyin runtime search remains in Java sources")
require('net.guizhanss.guizhanlib' not in java_sources, "GuizhanLib runtime imports remain in Java sources")
require('DisplayNameUtils.getDisplayName(' in java_sources, "Networks-owned item display-name bridge is not in use")
require('DisplayNameUtils.getMaterialName(' in java_sources, "Networks-owned material-name bridge is not in use")
require('services.gradle.org/distributions/gradle-9.4.1-bin.zip' in wrapper, "official Gradle wrapper URL is missing")

runtime_langs = sorted(p.name for p in (ROOT / "src/main/resources/lang").glob("*.yml"))
require(runtime_langs == ["en-US.yml"], f"unexpected runtime language files: {runtime_langs}")
require(not CJK.search(locale_text), "en-US.yml contains CJK characters")

current_ids = sorted((locale.get("items") or {}).keys())
require(current_ids == baseline_ids, f"item-ID drift detected: expected {len(baseline_ids)}, found {len(current_ids)}")
require(len(current_ids) == 288, f"expected 288 item IDs, found {len(current_ids)}")

verify_locale_structure(source_locale, locale)

allowed_cjk = {ROOT / "scripts/localization/zh-CN-source.yml"}
for base in [ROOT / "src", ROOT / ".github", ROOT / "README.md", ROOT / "LEGACY_COMPATIBILITY.md", ROOT / "CHANGELOG.md"]:
    paths = [base] if base.is_file() else list(base.rglob("*"))
    for path in paths:
        if not path.is_file() or path in allowed_cjk:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if CJK.search(text):
            ERRORS.append(f"player-facing CJK text remains in {path.relative_to(ROOT)}")

for required in [
    'repository: wickidcow/Slimefun-Legacy',
    'java-version: "25"',
    '-PslimefunLegacyJar=',
    'verify_legacy_compatibility.py',
    'verify_java21_bytecode.py',
]:
    require(required in workflow, f"workflow invariant missing: {required}")

if ERRORS:
    print("Networks Legacy compatibility verification failed:")
    for error in ERRORS:
        print(" -", error)
    sys.exit(1)

print(f"Networks Legacy verification passed: {len(current_ids)} item IDs, English runtime locale, Java 21 bytecode contract.")
