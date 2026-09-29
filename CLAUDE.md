# Canvas: dev notes

Canvas is [Patchy](https://github.com/SethRobinson/Patchy) (MIT, C++/Qt 6) built for Android
with Qt for Android, for Googlebooks. Plan and decisions: docs/PLAN.md. Research: docs/research/.

## Layout

- `UPSTREAM`: Patchy tag and commit, Qt and NDK versions. `VERSION`: versionName/versionCode.
- `patches/`: Canvas's changes to Patchy, `git format-patch` from branch `canvas` in the working
  copy `~/.cache/canvas/patchy`. Edit there, commit, then `./cv patches`. Patch commits use the
  user's noreply identity and the Co-Authored-By trailer.
- `android/`: `QT_ANDROID_PACKAGE_SOURCE_DIR`. The manifest (no permission placeholders, so no
  INTERNET or storage permissions; singleTask so "Open with" reaches the running window), icon,
  `CanvasActivity` (transparent caption bar over the menu bar's color), `CanvasFiles` (document
  copies for the file bridge, intent URIs), `CanvasPrint` (PDF to Android's print dialog).
- `THIRD_PARTY_NOTICES.md` from `tools/notices.py` (Patchy's notice sections, Qt's SBOMs from the
  kit, Android runtime, texts in `licenses/texts`). Rerun it when Qt or Patchy changes. The app
  embeds it (About > Licenses).

## Building

- Local, on this arm64 VM: `./cv setup` once (tools/toolchain.sh: aqtinstall Qt 6.11.3 for
  linux_arm64 host + Android arm64/x86_64, NDK r27c with its x86_64 clang/LLVM swapped for
  Debian's native clang 19 via wrapper scripts and a merged resource dir). Then `./cv src`,
  `./cv build [universal|arm64|x86_64]` → `build/Canvas-<abi>.apk`, signed with
  `~/.config/canvas/keystore.jks` when present. A cold arm64 compile takes a few minutes on 12
  cores; the universal APK about 6 minutes. AGP runs its x86_64 aapt2 through the VM's existing
  qemu-user binfmt; `~/Android/Sdk/ndk/27.2.12479018` links to the wrapped NDK so AGP strips.
- CI (`.github/workflows/build.yml`): stock NDK, x86_64 host Qt, one universal APK (Qt's
  multi-ABI build: `QT_ANDROID_ABIS`), uploaded unsigned; then a launch check in an Android 16
  x86_64 emulator. Runs queue (no cancel-in-progress).
- Patchy's build checks its translation catalogs against the sources, so Canvas's own UI text is
  plain `QStringLiteral` English (never `tr()`), or the check fails.

## Porting notes

- On Android Qt defines `Q_OS_LINUX` too; Canvas code uses `Q_OS_ANDROID` next to Patchy's
  `Q_OS_WASM` branches, which usually already mark the "no desktop" cases.
- Files: dialog_utils_android.cpp mirrors the web build's MEMFS bridge. Working copies live in
  `<AppData>/documents/<sha1(uri)[:16]>/<display name>` beside a `.uri` file; saves go back
  through `offer_browser_download_for_saved_file` (every save site already calls it);
  `open_document_path` refreshes a working copy from its document first.
- Recovery: the recovery folder is only discarded after a confirmed close (`quit_confirmed_`),
  and changed documents are written when the app is suspended or hidden.
- Deployment pulls Qt Quick in via the qmltooling plugins (about 11 MB per ABI);
  `qt_import_plugins` doesn't affect Android deployment. Not fixed yet.
- Qt PDF isn't published for Android: PDF import is the stub (export works).

## Device

HP Googlebook 14 (arm64, user 10), adb over Wireless debugging; ASUS Googlebook 14 for x86_64.
`./cv install|start|stop|logs|shot`. Never run emulators on this VM (CI only).
