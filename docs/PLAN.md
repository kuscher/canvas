# Canvas: plan (Patchy route)

A full-featured, open-source image editor for every Googlebook (Intel x86_64 and
Snapdragon arm64), as one ordinary APK. It is built from
[Patchy](https://github.com/SethRobinson/Patchy), a Photoshop-style editor in C++ and Qt 6,
using Qt for Android. The raw port comes first; after that, the app gets made to feel native.

Status: **proposal, waiting for approval** (2026-09-29). On 2026-09-29 the user chose Patchy over
the GIMP port after comparing alternatives. The GIMP plan is kept in
[archive/PLAN-gimp.md](archive/PLAN-gimp.md). Research:
[research/web-alternatives.md](research/web-alternatives.md),
[research/native-alternatives.md](research/native-alternatives.md), and the GIMP reports next to
them.

## Why Patchy

| | GIMP port (previous plan) | Patchy port (this plan) |
|---|---|---|
| Size of the job | Very large: about 150 libraries cross-compiled, plus an embedded X server | Medium: Qt comes prebuilt for Android, and Patchy vendors all of its libraries (miniz, stb, zstd, Little CMS, LibRaw) |
| Display | GTK3 inside an X server inside a SurfaceView | Qt draws straight into the Android window |
| Files | GIMP's own dialog, or a bridge built from scratch | Android's picker, following the pattern Patchy's web build already uses (copy in, edit, copy out) |
| Screen scaling | Whole numbers only (GTK3) | Fractional (Qt 6), so the HP's 1.125× panel stays sharp |
| Photo tools | All of GIMP | Heal, spot heal, patch, clone, dodge and burn; selections, magic wand, quick mask; adjustment layers; Smart Objects and Smart Filters; Liquify; text; vector shapes; Camera Raw |
| PSD | Good (88% render match in Patchy's own benchmark) | The best outside Adobe (98.8% in the same self-published benchmark), including PSB |
| Licence of the APK | GPL-3.0 | Permissive: Patchy is MIT, Qt is LGPL-3.0, LibRaw is CDDL-1.0 |
| Upstream | GIMP doesn't accept AI-assisted work | Patchy's README welcomes tested pull requests, including AI-assisted ones |

**What Patchy lacks compared with GIMP**
- XCF files.
- 16- and 32-bit editing, and CMYK editing.
- GEGL and G'MIC filters, and Script-Fu/Python plug-ins. Patchy scripts in JavaScript instead.
- 8BF plug-ins, which are Windows-only upstream.

**Project risk.** Patchy is four months old. Version 1.00 came out on 2026-09-27 and it has one maintainer. It was built with heavy AI assistance, and it releases every few days. Two things reduce that risk: we pin to a release tag and keep our changes as patches, and getting Android support accepted upstream would make it Patchy's own job.

## What the port has to deal with

From reading Patchy at v1.00 (commit a7837a5):

- **Build.** Patchy uses CMake and Qt 6 (Widgets, Svg, Network, Qml, PrintSupport, LinguistTools). Its code has branches for Windows, macOS, Linux and WebAssembly, but none for Android yet. On Android, Qt defines `Q_OS_LINUX`, so the Linux branches will run and each needs checking: `/proc/meminfo` works, but D-Bus portal handling and `QProcess` calls such as sounds and "reveal in folder" do not.
- **Things to turn off or replace**, most of which the web build already switches off:
  - Legacy 8BF plug-in host and scanner import (Windows and macOS only).
  - Update checker (Canvas has no internet permission).
  - Single-instance and MCP (AI-control) servers.
  - `QProcess` uses.
- **Printing.** Qt PrintSupport is doubtful on Android. The plan is to treat printing the way the web build does: make a PDF with `QPdfWriter` and hand it to Android's print dialog.
- **Qt PDF isn't published for Android.** Patchy already has a stub for that (it exports PDF but can't open one).
- **Files.** Patchy's core reads and writes `std::filesystem` paths. The web build solves the same problem in `dialog_utils_wasm.cpp`: it copies the picked file into a sandbox path, opens that, and copies saves back out. Canvas mirrors it in `dialog_utils_android.cpp`:
  - Android's picker (the Storage Access Framework) for Open.
  - "Create document" for Save As.
  - Persisted access, so Ctrl+S writes back to the same file and recent files keep working.
  - No broad storage permission.
- **Menus and windows.**
  - By default Qt on Android turns the menu bar into an Android options menu. `Qt::AA_DontUseNativeMenuBar` keeps the desktop menu bar.
  - Dialogs and floating panels are drawn inside the one app window (a Qt-on-Android limit).
  - The window has to resize freely without restarting, and must not go full screen (MixxxBook's patch 0009 lesson).
- **Pen.** Patchy already handles `QTabletEvent`/`QPointingDevice` pressure. We need to check what Qt on Android reports for tilt and eraser, and fill any gaps.
- **Fonts.** Android has no Arial or Times, which PSD text needs. The web build's metric-compatible fonts (Liberation, Carlito and others) get bundled; the CJK ones are skipped because Android has Noto CJK.
- **HEIC.** Upstream decodes it through WIC on Windows, Qt plugins on macOS, and WebCodecs on the web. On Android, Phase 2 uses Android's own decoder (ImageDecoder), which avoids HEVC licensing questions.

## Phases

### Phase 0: build and a first window

- Pin Patchy v1.00 in `UPSTREAM`. Patches live in `patches/`, formatted from a working branch.
- CI on GitHub Actions:
  - Qt 6.11 for Android (prebuilt, arm64 and x86_64) plus the host Qt, the NDK and JDK 17.
  - One multi-ABI build makes one APK for both chips.
  - ccache.
  - The output is an unsigned APK, which `./cv fetch` signs.
- The first patches: an Android CMake target, the Android manifest, the features above switched off, and the desktop menu bar.
- Manifest settings:
  - Resizable, with `configChanges` covering resizes and density.
  - No INTERNET permission.
  - A launcher icon.
  - The caption bar coloured to match the app.
- **Done when:** Patchy opens on the HP in a resizable window, and you can make a new image and paint on it with the mouse.

### Phase 1: the raw port → v0.1

- **Files:**
  - Open, Save, Save As and Export through Android's picker.
  - Ctrl+S writes back to the same file.
  - Recent files.
  - "Open with Canvas" from the Files app.
  - Document recovery saved when Android stops the app.
- **Input:** pen pressure (plus tilt and eraser if Qt reports them), right-click, wheel and trackpad scrolling, keyboard shortcuts, and text on the clipboard.
- **Look:** fractional scaling checked on the HP (1.125×) and the ASUS (1.625×), light and dark themes following the system, and bundled fonts for PSD text.
- **Print** through Android's print dialog.
- **Memory:** budgets checked against Android 17's per-app memory limits, using a big PSD test.
- **Both chips:** arm64 on the HP, x86_64 on the ASUS, and an x86_64 emulator smoke test in CI.
- **Licences:** THIRD_PARTY_NOTICES, an About & licences page, and sources with every release.
- **Done when**, on both Googlebooks:
  - A JPEG, PNG and layered PSD open from Files.
  - Heal, clone, an adjustment layer and text all work.
  - The PSD saves back in place and still opens in Photoshop-compatible readers.
  - PNG export and printing work.
  - Pen pressure paints.
  - Settings survive a restart.

### Phase 2: at home on a Googlebook

- **Clipboard and drag-and-drop:** images on the clipboard in both directions, and dragging images from the Files app into the window.
- **Trackpad:** pinch to zoom and two-finger rotate, mapped to Patchy's zoom and rotate.
- **Formats:** HEIC and AVIF through Android's decoders.
- **Getting pictures in:** "Import from camera" through Android's camera, replacing scanner import. Share and "Send to" other apps.
- **Launcher:** shortcuts for "New image", "Paste as new image" and recent files.
- **Look:** a Googlebook theme option (system font, Material colours) next to Patchy's own themes.
- **Keyboard shortcut helper:** Meta + / lists Patchy's shortcuts.

### Phase 3: upstream and polish

- Offer the Android port to Patchy (your call, see Questions). Then keep Canvas current with Patchy releases through a rebase script.
- Lower pen latency and better pen-button mapping.

## Repos, builds and uploads

| What | Where |
|---|---|
| Source | `~/canvas` → github.com/kuscher/canvas (private). The working Patchy checkout is `~/.cache/canvas/patchy`, on branch `canvas`. |
| Helper | `./cv ci`, `fetch`, `install`, `start`, `logs`, `shot`, `release [--publish]` |
| CI | GitHub Actions, ubuntu-24.04 x86_64: Qt from Qt's servers (aqtinstall), NDK, one multi-ABI APK, and an x86_64 emulator smoke test |
| Signing | `~/.config/canvas/keystore.jks` + `keystore.pass`, never in git. The keystore is backed up to private storage (folder a private folder, with a README); the password goes in your password manager. |
| Releases | `Canvas.apk` (stable name, so `releases/latest/download/Canvas.apk` works), `Canvas-<v>-source.tar.gz` (this repo plus Patchy at the pinned tag plus patches), a Qt source link or archive (LGPL), `SHA256SUMS`, and notes from `CHANGELOG.md` |
| README | In the style of DiscoBar: icon, "⬇ Download Canvas.apk", screenshots, and a step-by-step install. It says clearly that it is built from Patchy by Seth Robinson and that he doesn't endorse this build. It has the passion-project note, "Made on a Googlebook", and a tiny Claude credit. |
| Device | Builds go into the HP's Download folder and are installed over adb. The ASUS covers x86_64. |
| Licences | Canvas's own code and patches are MIT, like Patchy. The APK bundles Qt (LGPL-3.0, as shared libraries), LibRaw (CDDL-1.0), Little CMS, zstd, miniz and stb (MIT/BSD/public domain), and OFL fonts. No GPL. |

## Questions

1. **The name.**
   - Keep "Canvas". It overlaps a live graphics-software trademark and Google's Chrome Canvas.
   - Use "Patchy", the upstream name, if the port is going to become Patchy's official Android build.
   - Use "CanvasBook".
2. **Upstream.**
   - Build downstream first, then decide on a pull request to Patchy after v0.1. Patchy's repo rules forbid AI co-author lines in PRs, so you would submit it yourself.
   - Work upstream-first on a Patchy fork branch.
   - Keep it downstream only.
3. **Repo visibility.**
   - Public now: free 4-core CI runners, and it's friendlier to upstream.
   - Private until v0.1. CI is much smaller than for GIMP, so private minutes probably suffice.
4. **Scope.**
   - Phase 0 and 1 through to v0.1, checking in only for decisions and hands-on tests.
   - Phase 0 first, then a check-in.
