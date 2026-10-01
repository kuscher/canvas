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
  multi-ABI build: `QT_ANDROID_ABIS`) and the same build as an App Bundle, uploaded unsigned; then
  a launch check in an Android 16 x86_64 emulator. Runs queue (no cancel-in-progress).
  `release.yml` calls this workflow for a tag and signs what it made in a separate job.
- Patchy's build checks its translation catalogs against the sources, so Canvas's own UI text is
  plain `QStringLiteral` English (never `tr()`), or the check fails.

## Branding and releases

- The logo is `android/brand/canvas-logo.svg` (a white C on #6F3FE0), passed to the build as
  `PATCHY_ANDROID_LOGO_FILE` for the start page; the launcher icon is the adaptive icon in
  `android/res` (same C, sized for the 66 dp safe zone). After an icon change the launcher keeps
  its cached icon; restarting the launcher can leave the desktop taskbar stashed (open and close
  Overview twice to bring it back), so prefer telling the user it refreshes on its own.
- `./cv build` refreshes Canvas's cache variables (VERSION, notices, logo) on every run.
- Release (docs/RELEASING.md): bump `VERSION` (name and code), add a `CHANGELOG.md` section and the
  Play text (`store-submission/listing/en-US/release-notes.txt`, 500 characters at most), push, then
  `git tag vX && git push origin vX`. GitHub builds (`build.yml`, no key), signs and publishes
  (`release.yml`): Canvas.apk, the source tarball with the patched Patchy, the Qt module sources and
  SHA256SUMS as a GitHub release, and the bundle as a draft on Play's closed testing. Sending the
  draft for review stays a button in the Play Console. No key file needed: the key (a new one since
  2026-09-30, also Google Play's) is in the `release` environment's secrets on GitHub, backed up with
  its password in the user's a private folder.
  "Run workflow" on release.yml is a dry run (same build, signing and checks, nothing published).
- Fallback on a machine that has the key in `~/.config/canvas`: `./cv build universal`, then
  `tools/release.sh` (checks signature, version, ABIs and a clean tree; writes dist/v<version>) and
  `tools/release.sh --publish` (its tag also starts release.yml, which leaves the release alone and
  sends the bundle to Play).
- README screenshots: `tools/demo_art.py` writes the demo PSD (put it in Downloads with
  `content insert/write --user 10` on content://media/external/downloads, open it with Canvas's
  Open…, delete the row afterwards), capture a 1536x960 window with `./cv shot`, then
  `tools/readme_images.py`. Never show the user's own files or recent-file names.

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
- The system picker hides files whose MIME type doesn't exactly match Qt's (PSD: image/x-photoshop
  on Android, image/vnd.adobe.photoshop in Qt), so Open offers every file; picker URIs must stay
  `QUrl::FullyEncoded` (the pretty form decodes %20 and the grant no longer matches).
- Every Qt top-level window (each menu, each combo popup) is a view in the activity's layout;
  popups draw in TextureViews over the main window's SurfaceView, so a popup behind the main
  window's view is open but invisible. Switching menus along the menu bar raised the main window
  over the new menu; `keep_popups_in_front()` (android_window.cpp) raises open popups again.
- The system caption over the menu bar sometimes cancels a mouse or trackpad press right after
  it lands (ACTION_CANCEL). Qt for Android ignores mouse cancels and would keep the button held
  and its pointer grab; `CanvasActivity.dispatchTouchEvent` turns such a cancel into a release.
- The HP's touchpad sends mouse-source events with a finger tool type, which Qt for Android
  routes through its touch path (clicks become touches); a real mouse goes the mouse path.
  A two-finger trackpad scroll arrives as one fake finger dragging from the pointer
  (CLASSIFICATION_TWO_FINGER_SWIPE) and would paint; a pinch as CLASSIFICATION_PINCH with a
  per-sample AXIS_GESTURE_PINCH_SCALE_FACTOR, several samples batched into one event's history.
  CanvasGestures turns both into pans and zooms; two-finger pans call CanvasWidget::pan_view_by,
  never wheel events (Patchy's wheel zooms by default).
- QScreen's geometry lags the platform screen's (a queued event): size the main window from
  `screen->handle()->availableGeometry()` (android_window.cpp, links Qt6::GuiPrivate).

## Device

HP Googlebook 14 (arm64, user 10), adb over Wireless debugging; ASUS Googlebook 14 for x86_64.
`./cv install|start|stop|logs|shot`. Never run emulators on this VM (CI only).

Input for tests: `input touchscreen tap/swipe/motionevent` works; `input mouse` doesn't. For
hover and real clicks, register a relative mouse with `adb shell uinput -` (JSON commands on
stdin, kept open through a FIFO) and find the pointer by diffing two screenshots around a small
move (screencap includes the pointer; slow moves travel about 4.4 px per count). Never park the
pointer in a screen corner: the top-left corner opens Overview. uinput also makes a multi-touch
touchscreen (INPUT_PROP_DIRECT, ABS_MT_*) and a touchpad (INPUT_PROP_POINTER + BUTTONPAD, with
resolutions) that Android runs through its real trackpad gestures; test gestures on a new blank
document, never on the user's. Close a menu by choosing an
item or tapping clear of it; a "tap outside" can land on an item of a menu that is open.
