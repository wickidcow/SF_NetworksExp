#!/usr/bin/env bash
set -euo pipefail

VERSION="2.1.67"
FILE="SF_JustEnoughGuide${VERSION}.jar"
EXPECTED_SHA256="8456be1a05715f38536f217ad2136aff18e38b6e9591a41c9814e89d0576253a"
DESTINATION="${1:-.ci/jeg/${FILE}}"
URL="https://github.com/wickidcow/SF_JustEnoughGuide/releases/download/v${VERSION}/${FILE}"
TEMP="${DESTINATION}.tmp"

mkdir -p "$(dirname "$DESTINATION")"
rm -f "$TEMP"

curl   --fail   --location   --silent   --show-error   --retry 6   --retry-delay 2   --retry-all-errors   --connect-timeout 20   --max-time 180   --output "$TEMP"   "$URL"

test -s "$TEMP"
printf '%s  %s\n' "$EXPECTED_SHA256" "$TEMP" | sha256sum --check --status
unzip -tq "$TEMP" >/dev/null
unzip -Z1 "$TEMP" | grep -Fxq 'com/balugaq/jeg/api/recipe_complete/source/RecipeCompleteProvider.class'

mv "$TEMP" "$DESTINATION"
printf 'Verified maintained JEG %s at %s\n' "$VERSION" "$DESTINATION"
