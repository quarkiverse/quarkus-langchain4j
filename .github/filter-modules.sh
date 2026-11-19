#!/usr/bin/env bash
# Filter a candidate module list down to those impacted.
# Usage: filter-modules.sh <impacted-file> <base-prefix> <candidates-json-array>
# Prints a compact JSON array of the impacted candidates (input order preserved).
set -uo pipefail

impacted_file="$1"
base_prefix="$2"
candidates="$3"

# ALL sentinel -> return candidates unchanged (compacted).
if [ "$(cat "$impacted_file")" = "ALL" ]; then
  jq -c '.' <<<"$candidates"
  exit 0
fi

# Build a JSON array of impacted paths.
impacted_json="$(sed '/^$/d' "$impacted_file" | jq -R -s -c 'split("\n") | map(select(length > 0))')"

# A candidate C is impacted iff, with X = base-prefix joined to C, some impacted
# path P equals X or is under X/.
jq -c --arg base "$base_prefix" --argjson impacted "$impacted_json" '
  map(
    . as $c
    | (if $base == "" then $c else $base + "/" + $c end) as $x
    | select(any($impacted[]; . == $x or startswith($x + "/")))
  )
' <<<"$candidates"
