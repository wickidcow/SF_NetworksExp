#!/usr/bin/env bash
set -euo pipefail

core_dir="${1:?Usage: build_slimefun_core.sh <core-repository> [core-key]}"
core_key="${2:-unknown}"
cd "$core_dir"

run_core_command() {
  if [[ "${GITHUB_ACTIONS:-false}" != "true" ]]; then
    "$@"
    return
  fi

  local safe_key="${core_key//[^A-Za-z0-9_.-]/_}"
  local log_file="${RUNNER_TEMP:-/tmp}/networks-${safe_key}-core-build.log"

  echo "Building Slimefun core dependency (${core_key}); compiler output is suppressed unless the command fails."
  if "$@" >"$log_file" 2>&1; then
    echo "Slimefun core dependency command completed successfully."
  else
    local status=$?
    echo "::group::Slimefun core build log (${core_key})"
    cat "$log_file"
    echo "::endgroup::"
    return "$status"
  fi
}

if [[ "$core_key" == "legacy" && -f scripts/verify_legacy.py ]]; then
  python3 scripts/verify_legacy.py .
fi

if [[ -x ./gradlew || -f ./gradlew ]]; then
  chmod +x ./gradlew

  case "$core_key" in
    legacy)
      run_core_command ./gradlew spotlessApply --no-daemon
      run_core_command ./gradlew clean build --no-daemon
      ;;
    gugu)
      # Networks only needs the exact Gugu plugin JAR for compilation.
      # Do not make Networks compatibility depend on Gugu's separate test suite.
      run_core_command ./gradlew clean shadowJar -x test --no-daemon
      ;;
    *)
      run_core_command ./gradlew clean build -x test --no-daemon
      ;;
  esac
elif [[ -x ./mvnw || -f ./mvnw ]]; then
  chmod +x ./mvnw
  run_core_command ./mvnw --batch-mode --no-transfer-progress -DskipTests clean package
elif [[ -f pom.xml ]]; then
  run_core_command mvn --batch-mode --no-transfer-progress -DskipTests clean package
else
  echo "Unsupported Slimefun core build layout in $PWD" >&2
  exit 1
fi
