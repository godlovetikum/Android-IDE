#!/usr/bin/env bash
# Download one pinned Termux bootstrap archive for the requested APK ABI.
# The archive is generated into android-ide/android/assets/termux at build time;
# it is intentionally ignored by Git so the repository does not contain every ABI.
set -euo pipefail

# Gradle passes the selected ABI as the first argument. It must take
# precedence over a developer or CI environment variable so the bootstrap
# archive and APK ABI can never silently diverge.
ABI="${1:-${TERMUX_ABI:-arm64-v8a}}"
TAG="bootstrap-2026.09.13-r1+apt.android-7"
BASE_URL="https://github.com/termux/termux-packages/releases/download/${TAG}"
OUT_DIR="$(cd "$(dirname "$0")/../android-ide/android/assets/termux" && pwd)"
mkdir -p "$OUT_DIR"

case "$ABI" in
  arm64-v8a) ASSET="bootstrap-aarch64.zip"; SHA="dbf2805613ff2ace0b233c3b349e080bb0ff358f4ad64f4cca5966b27b93e7ee" ;;
  armeabi-v7a) ASSET="bootstrap-arm.zip"; SHA="ac65b4c4aa10322a9a54f8ec3e3122dec1371bef4834db4455503df0fc33f521" ;;
  x86) ASSET="bootstrap-i686.zip"; SHA="790481a60eb90dfcbaccc27113c11fa14d6820215bec7860b9a03e3417ec827" ;;
  x86_64) ASSET="bootstrap-x86_64.zip"; SHA="e5b7ce18642c6769acf6cd05586170191fc98f1882ddbde6b5fb5f0b1dc253f0" ;;
  *) echo "Unsupported TERMUX_ABI: $ABI" >&2; exit 2 ;;
esac

TARGET="$OUT_DIR/$ASSET"
if [[ ! -f "$TARGET" ]] || [[ "$(sha256sum "$TARGET" | cut -d' ' -f1)" != "$SHA" ]]; then
  TMP="$TARGET.partial"
  trap 'rm -f "$TMP"' EXIT
  curl --fail --location --retry 3 --silent --show-error "$BASE_URL/$ASSET" -o "$TMP"
  printf '%s  %s\n' "$SHA" "$TMP" | sha256sum -c -
  mv "$TMP" "$TARGET"
  trap - EXIT
fi

# Keep only the selected archive in the generated assets directory.
find "$OUT_DIR" -maxdepth 1 -type f -name 'bootstrap-*.zip' ! -name "$ASSET" -delete
printf 'Prepared pinned Termux bootstrap %s for %s at %s\n' "$TAG" "$ABI" "$TARGET"
