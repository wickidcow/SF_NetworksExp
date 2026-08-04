#!/usr/bin/env bash
set -euo pipefail

core_dir="${1:?Usage: build_slimefun_core.sh <core-repository> [core-key]}"
core_key="${2:-unknown}"
cd "$core_dir"

if [[ "$core_key" == "legacy" && -f scripts/verify_legacy.py ]]; then
  python3 scripts/verify_legacy.py .
fi

if [[ -x ./gradlew || -f ./gradlew ]]; then
  chmod +x ./gradlew

  case "$core_key" in
    legacy)
      ./gradlew spotlessApply --no-daemon
      ./gradlew clean build --no-daemon
      ;;
    gugu)
      # Networks only needs the exact Gugu plugin JAR for compilation.
      # Do not make Networks compatibility depend on Gugu's separate test suite.
      ./gradlew clean shadowJar -x test --no-daemon
      ;;
    *)
      ./gradlew clean build -x test --no-daemon
      ;;
  esac
elif [[ -x ./mvnw || -f ./mvnw ]]; then
  chmod +x ./mvnw
  ./mvnw --batch-mode --no-transfer-progress -DskipTests clean package
elif [[ -f pom.xml ]]; then
  mvn --batch-mode --no-transfer-progress -DskipTests clean package
else
  echo "Unsupported Slimefun core build layout in $PWD" >&2
  exit 1
fi
