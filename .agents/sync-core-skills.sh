#!/usr/bin/env bash
set -euo pipefail

REF="${1:-main}"
SKILLS="coding-style writing-tests working-with-config pull-requests writing-build-steps"
REPO="quarkusio/quarkus"
DEST="$(cd "$(dirname "$0")" && pwd)/skills"

if [[ "$REF" =~ ^[0-9a-f]{40}$ ]]; then
  SHA="$REF"
else
  SHA="$(git ls-remote "https://github.com/${REPO}.git" "refs/heads/${REF}" | cut -f1)"
fi
[[ -n "$SHA" ]] || { echo "Cannot resolve ${REF}" >&2; exit 1; }

for skill in $SKILLS; do
  mkdir -p "${DEST}/${skill}"
  curl -fsSL "https://raw.githubusercontent.com/${REPO}/${SHA}/.agents/skills/${skill}/SKILL.md" \
    -o "${DEST}/${skill}/SKILL.md"
done

cat > "${DEST}/UPSTREAM.md" <<DOC
# Upstream skills

The skills in this directory are verbatim copies from Quarkus core. Do not edit them here.
Repository-specific rules belong in the root AGENTS.md.

- Source: https://github.com/${REPO}/tree/${SHA}/.agents/skills
- Commit: ${SHA}
- Skills: ${SKILLS}

To refresh, run \`.agents/sync-core-skills.sh [ref]\` and open a PR with the result.
See what changed upstream with
\`git diff <old-commit>..<new-commit> -- .agents/skills\` in a Quarkus checkout.
DOC

echo "Synced ${SKILLS} from ${REPO}@${SHA}"
