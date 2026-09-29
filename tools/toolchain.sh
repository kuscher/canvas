#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Sets up the local Android toolchain in $CANVAS_CACHE (default ~/.cache/canvas)
# on this arm64 Debian VM, without emulating anything:
#
#   qt/<ver>/gcc_arm64          Qt host tools (moc, rcc, lrelease, androiddeployqt)
#   qt/<ver>/android_arm64_v8a  Qt for Android, arm64
#   qt/<ver>/android_x86_64     Qt for Android, x86_64
#   ndk/android-ndk-<rel>       the NDK, with its x86_64 clang and LLVM tools
#                               swapped for Debian's native clang 19 / LLVM 19.
#                               The NDK's sysroot, libc++ and Android runtime
#                               libraries (compiler-rt builtins, libunwind) stay,
#                               through a merged clang resource directory.
#
# CI uses the stock NDK on x86_64 runners; this only makes local builds fast.
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."
. ./UPSTREAM
C=${CANVAS_CACHE:-$HOME/.cache/canvas}
mkdir -p "$C"

need() { dpkg -s "$@" >/dev/null 2>&1 || sudo apt-get install -y -q "$@"; }
need clang-19 lld-19 llvm-19 cmake ninja-build ccache unzip python3-venv

[[ -x $C/venv/bin/aqt ]] || { python3 -m venv "$C/venv" && "$C/venv/bin/pip" install -q aqtinstall; }
Q=$C/qt/$QT_VERSION
[[ -d $Q/gcc_arm64 ]] || "$C/venv/bin/aqt" install-qt linux_arm64 desktop "$QT_VERSION" linux_gcc_arm64 -O "$C/qt" -m qtimageformats
for abi in arm64_v8a x86_64; do
  [[ -d $Q/android_$abi ]] || "$C/venv/bin/aqt" install-qt all_os android "$QT_VERSION" "android_$abi" -O "$C/qt" -m qtimageformats
done

N=$C/ndk/android-ndk-$NDK_RELEASE
if [[ ! -f $N/.canvas-native ]]; then
  [[ -f $C/ndk-$NDK_RELEASE.zip ]] ||
    curl -fL -o "$C/ndk-$NDK_RELEASE.zip" "https://dl.google.com/android/repository/android-ndk-$NDK_RELEASE-linux.zip"
  rm -rf "$N" && mkdir -p "$C/ndk" && unzip -q "$C/ndk-$NDK_RELEASE.zip" -d "$C/ndk"
  P=$N/toolchains/llvm/prebuilt/linux-x86_64
  R=$C/ndk-host/clang-resource-$NDK_RELEASE
  mkdir -p "$R/lib"
  ln -sfn /usr/lib/llvm-19/lib/clang/19/include "$R/include"
  ln -sfn "$(ls -d "$P"/lib/clang/*/lib/linux)" "$R/lib/linux"
  for t in clang clang++ clang-[0-9]*; do
    [[ -e $P/bin/$t ]] || continue
    real=/usr/lib/llvm-19/bin/clang; [[ $t == clang++ ]] && real=/usr/lib/llvm-19/bin/clang++
    cat > "$P/bin/$t" <<WRAP
#!/bin/sh
# Canvas (tools/toolchain.sh): Debian's native clang 19 in place of the NDK's x86_64 clang.
exec $real -resource-dir=$R -fuse-ld=lld --rtlib=compiler-rt --unwindlib=libunwind -stdlib=libc++ -Qunused-arguments --sysroot=$P/sysroot "\$@"
WRAP
    chmod +x "$P/bin/$t"
  done
  for t in ld.lld lld ld64.lld lld-link llvm-addr2line llvm-ar llvm-as llvm-cov llvm-cxxfilt llvm-dis \
           llvm-dwarfdump llvm-link llvm-nm llvm-objcopy llvm-objdump llvm-profdata llvm-ranlib \
           llvm-readelf llvm-readobj llvm-size llvm-strings llvm-strip llvm-symbolizer clang-scan-deps; do
    [[ -e /usr/lib/llvm-19/bin/$t ]] && ln -sfn "/usr/lib/llvm-19/bin/$t" "$P/bin/$t"
  done
  touch "$N/.canvas-native"
fi
echo "toolchain ready in $C"
