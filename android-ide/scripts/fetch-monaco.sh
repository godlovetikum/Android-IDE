#!/usr/bin/env bash
# android-ide/scripts/fetch-monaco.sh
#
# Downloads the Monaco Editor AMD bundle into android/assets/editor/vs/.
#
# USAGE
#   Run from the android-ide/ directory:
#     bash scripts/fetch-monaco.sh
#
# WHY THIS SCRIPT EXISTS
#   The vs/ directory is git-ignored (~20 MB of generated JS) and must be
#   re-materialised in two situations:
#     1. CI — run automatically by the GitHub Actions workflow before `./gradlew`.
#     2. Local first checkout — run once manually after cloning.
#
# PINNING
#   MONACO_VERSION is pinned here. To upgrade Monaco:
#     1. Change MONACO_VERSION below.
#     2. Update the version comment in android/assets/editor/index.html.
#     3. Update the comment in android/assets/editor/monaco-init.js.
#   The HTML and JS files use RELATIVE paths (vs/loader.js, vs) so the
#   version string does NOT appear in any URL — only in this comment block.
#
# DEPENDENCIES
#   npm (bundled with Node.js; pre-installed on ubuntu-latest GitHub runners).

set -euo pipefail

MONACO_VERSION="0.52.0"
TARGET_DIR="android/assets/editor/vs"

# ── Idempotency check ────────────────────────────────────────────────────────
# Skip download if vs/loader.js already exists (correct version assumed).
# Delete the vs/ directory to force a re-download.
if [ -d "$TARGET_DIR" ] && [ -f "$TARGET_DIR/loader.js" ]; then
    echo "Monaco ${MONACO_VERSION} already present at ${TARGET_DIR} — skipping."
    exit 0
fi

echo "Fetching monaco-editor@${MONACO_VERSION} from npm registry…"

# ── Isolated install ─────────────────────────────────────────────────────────
# --prefix puts node_modules in a temp dir so we don't pollute the workspace.
# --ignore-scripts skips any post-install hooks.
# --no-save does not write/modify any package.json.
# --silent suppresses npm progress bars in CI logs.
WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

npm install \
    --prefix    "$WORK_DIR" \
    "monaco-editor@${MONACO_VERSION}" \
    --ignore-scripts \
    --no-save \
    --silent

SRC="$WORK_DIR/node_modules/monaco-editor/min/vs"

if [ ! -d "$SRC" ]; then
    echo "ERROR: Expected $SRC after npm install — npm package layout may have changed." >&2
    exit 1
fi

# ── Copy into assets ─────────────────────────────────────────────────────────
mkdir -p "$TARGET_DIR"
# Use trailing slash on $SRC/. to copy CONTENTS of vs/, not the vs/ dir itself.
cp -r "$SRC/." "$TARGET_DIR/"

JS_COUNT=$(find "$TARGET_DIR" -name '*.js' | wc -l | tr -d ' ')
echo "Done. Monaco ${MONACO_VERSION} → ${TARGET_DIR}  (${JS_COUNT} JS files)"
