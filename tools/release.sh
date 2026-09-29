#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Release files for the version in VERSION, in dist/v<version>/:
#
#   Canvas.apk                  build/Canvas-universal.apk (Arm and x86_64), which must be
#                               signed with the release key and carry this version
#   Canvas-<v>-source.tar.gz    this repo at HEAD, with the patched Patchy source (branch
#                               `canvas` of the working copy, including Patchy's bundled
#                               libraries) under patchy/
#   qt*-everywhere-src-*.tar.xz  the source of the Qt modules the app ships (LGPL-3.0),
#                               from Qt's own repository via aqtinstall
#   SHA256SUMS
#
#   tools/release.sh [--publish]
#
# --publish tags v<version>, pushes the tag and creates the GitHub release with these files
# and the version's notes from CHANGELOG.md.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
C=$HOME/.cache/canvas
SRC=$C/patchy
# shellcheck source=/dev/null
source <(grep -E '^[A-Z_]+=' "$ROOT/UPSTREAM" "$ROOT/VERSION" | cut -d: -f2-)
V=$VERSION_NAME
OUT=$ROOT/dist/v$V
RELEASE_CERT=e0f8b1332b19c09cda26fac4f73300a157ae9639fc3f37bcd8804677a38684df
QT_MODULES=(qtbase qtdeclarative qtsvg qtimageformats)
die() { echo "release: $*" >&2; exit 1; }

apk=$ROOT/build/Canvas-universal.apk
[[ -f $apk ]] || die "no $apk: run ./cv build universal"
cert=$(apksigner verify --print-certs "$apk" | sed -n 's/.*SHA-256 digest: //p' | head -1)
[[ $cert == "$RELEASE_CERT" ]] || die "Canvas-universal.apk isn't signed with the release key ($cert)"
aapt2=$(ls "$HOME"/Android/Sdk/build-tools/*/aapt2 | tail -1)
badging=$("$aapt2" dump badging "$apk" 2>/dev/null | head -1)
[[ $badging == *"versionCode='$VERSION_CODE'"*"versionName='$V'"* ]] || die "APK version doesn't match VERSION: $badging"
abis=$(unzip -l "$apk" | grep -oE 'lib/[a-z0-9_-]+/libc\+\+_shared.so' | cut -d/ -f2 | sort | tr '\n' ' ')
[[ $abis == "arm64-v8a x86_64 " ]] || die "expected arm64-v8a and x86_64 in the APK, got: $abis"

git -C "$ROOT" diff --quiet HEAD || die "the repo has uncommitted changes"
git -C "$SRC" diff --quiet HEAD || die "the Patchy working copy has uncommitted changes"
[[ $(git -C "$SRC" rev-parse HEAD) == $(git -C "$SRC" rev-parse canvas) ]] || die "the Patchy working copy isn't on branch canvas"
expected=$(ls "$ROOT"/patches/*.patch | wc -l)
actual=$(git -C "$SRC" rev-list --count "$PATCHY_COMMIT..canvas")
[[ $expected == "$actual" ]] || die "patches/ has $expected patches but branch canvas has $actual commits: run ./cv patches"

rm -rf "$OUT"
mkdir -p "$OUT"
cp "$apk" "$OUT/Canvas.apk"

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
git -C "$ROOT" archive --format=tar --prefix="canvas-$V/" HEAD >"$tmp/canvas.tar"
git -C "$SRC" archive --format=tar --prefix="canvas-$V/patchy/" canvas >"$tmp/patchy.tar"
tar --concatenate --file="$tmp/canvas.tar" "$tmp/patchy.tar"
gzip -9n <"$tmp/canvas.tar" >"$OUT/Canvas-$V-source.tar.gz"

for m in "${QT_MODULES[@]}"; do
  f=$(ls "$C/qt-src/archives/$m-everywhere-src-$QT_VERSION".tar.* 2>/dev/null | head -1)
  [[ -n $f ]] || die "no $m source archive in $C/qt-src/archives (aqt install-src linux $QT_VERSION --archives ${QT_MODULES[*]} --keep --archive-dest $C/qt-src/archives -O $C/qt-src/extract)"
  cp "$f" "$OUT/"
done

(cd "$OUT" && sha256sum -- * >SHA256SUMS)
ls -la "$OUT"

[[ ${1:-} == --publish ]] || exit 0
notes=$tmp/notes.md
awk -v v="$V" '$0 ~ "^## " v "( |$)" {on=1; next} /^## / {on=0} on' "$ROOT/CHANGELOG.md" >"$notes"
[[ -s $notes ]] || die "CHANGELOG.md has no section for $V"
cat >>"$notes" <<EOF

**Install:** download **Canvas.apk** below, open it from your Downloads and allow the install
(see [Install](https://github.com/kuscher/canvas#install)). One APK for every Googlebook, Intel and
Snapdragon.

**Sources:** Canvas-$V-source.tar.gz is the complete source of the app (Canvas and the patched
Patchy, with its bundled libraries); the qt*-everywhere-src archives are the source of the Qt
modules it ships. SHA256SUMS lists every file's checksum.
EOF
git -C "$ROOT" tag -a "v$V" -m "Canvas $V"
git -C "$ROOT" push -q origin "v$V"
gh release create "v$V" --repo kuscher/canvas --title "Canvas $V" --notes-file "$notes" "$OUT"/*
