# Canvas: plan

GIMP for Googlebooks. A real GIMP 3.2, built for Android and shipped as one APK
that runs on every Googlebook (Intel x86_64 and Snapdragon arm64): no Linux VM,
no Termux, no root. It should feel like a great image editor that belongs on
the laptop. The raw port comes first; the phases after it make it feel native.

Status: **proposal, waiting for approval** (2026-09-29). Research behind it:
[research/gimp-upstream.md](research/gimp-upstream.md) (GIMP versions, build,
licences, the name) and [research/android-routes.md](research/android-routes.md)
(ways to put a GTK app on Android, prior art, platform rules).

## Which GIMP

| Series | State (2026-09-29) | For Canvas |
|---|---|---|
| 2.10.38 | Last GTK2 release (May 2024), no more backports | No: dead end |
| 3.0.x | Ended at 3.0.8 (Jan 2026), "might be the final release" | No: unmaintained |
| **3.2.x** | **3.2.6, 2026-09-10**; the only maintained stable branch | **Yes: GIMP 3.2.6 with babl 0.1.128 and GEGL 0.4.72** |
| 3.3.x → 3.4 | Master; no dev release yet; 3.4 brings native file choosers and a new project format | Rebase when 3.4 ships |
| GTK4 / "GIMP 4" | "Not yet planning a GTK4 port" | Long-term route to real Android windows |

Why 3.2.6:

- It is what the GIMP team maintains, and it has 28 CVE fixes that matter for an app that opens files from anywhere.
- It has the pen features a laptop with a stylus wants: MyPaint brushes v2, pressure gain, and stylus barrel rotation (new in 3.2.6).
- It adds link and vector layers, non-destructive filters everywhere, and new formats: APNG, JPEG 2000, PSB export, SVG and vector PDF export.
- The plug-in API is stable for all of 3.x, so every GIMP 3 plug-in, tutorial and script carries over.
- It already has aarch64 builds on Linux (AppImage, Flatpak, Snap) and ARM64 builds on Windows and macOS, so ARM code paths are exercised upstream.

Termux packages GIMP 3.2.0 for Android phones (with Termux:X11). That proves GIMP 3.2 compiles and runs on Android's C library, and its recipes are our starting point. Termux notes that going past 3.2.0 needs rework, and closing that gap to 3.2.6 is part of Phase 1.

## The route I propose: native GIMP with a built-in X display

GIMP is a GTK3 program, and GTK3 cannot draw on Android by itself. GTK4 has an Android backend, but GIMP is not on GTK4. So the display has to come from somewhere, and we compared four ways to get it:

| | A. Native GIMP + built-in X display (**proposed**) | B. GTK3 Broadway in a WebView | C. GTK4 Android backend | D. GIMP in the Linux Terminal VM |
|---|---|---|---|---|
| Works today | Yes | Yes, badly | No: GIMP has no GTK4 port | Yes: `apt install gimp` gives 3.0.4 |
| Speed | Native CPU, zero-copy frames to the screen | Every frame zlib-compressed and redrawn in JavaScript | Would be best | Software-rendered inside the VM |
| Pen | Pressure, tilt, eraser | No pressure | Yes | Unknown |
| Feels native | One real resizable window; more with Phases 2 and 3 | One web page pretending to be windows | Real Android windows | A window inside the Terminal |
| Shareable APK | Yes | Yes | n/a | No: only on a set-up Terminal, and the VM stops when the Terminal closes |

**How route A works**

```
 Canvas.apk
 ┌──────────────────────────────────────────────────────────────────────┐
 │ Canvas window (Activity)       ── the Googlebook window you resize   │
 │   SurfaceView ◄── frames (AHardwareBuffer, zero copy) ──┐            │
 │   mouse, keys, pen ──────────────────────────────────► X server      │
 │                                                        (lorie, from  │
 │                                                        termux-x11)   │
 │                                                           ▲ X11 over │
 │                                                           │ a socket │
 │ GIMP 3.2.6 process (GTK3, GEGL, babl) ────────────────────┘          │
 │   └─ plug-ins: ~120 small programs (file formats, filters,           │
 │      Script-Fu, Python 3), started as GIMP needs them                │
 │                                                                      │
 │ Every program and library ships as lib*.so in the APK's native-      │
 │ library folder (the only place Android lets an app run code from).   │
 │ Brushes, icons, themes, Python's library and translations unpack     │
 │ once into the app's storage, in a folder tree whose program entries  │
 │ are links back into the native-library folder.                       │
 └──────────────────────────────────────────────────────────────────────┘
```

- **The display.** termux-x11's X server ("lorie") became an embeddable Android library on 2026-07-24. It draws into an Android SurfaceView through AHardwareBuffers. It resizes the X screen when the window is resized, and it exposes Android pens to X as pressure, tilt and eraser devices, the same way a Wacom tablet appears on Linux. GIMP runs in single-window mode, filling the Canvas window.
- **The build.** The whole stack is cross-compiled from source with Android's NDK: GIMP, GEGL, babl, GTK3, Python and about 150 libraries. We use Termux's build recipes and patches under Canvas's own app ID, in GitHub Actions on x86_64 runners. The same build runs for arm64 and x86_64 (Android's NDK has no arm64 host and emulating on the VM is off the table).
- **Where the files go.** Googlebooks run the human as Android user 10 (headless system user mode). Stock Termux refuses to run there because its paths assume user 0, which also rules out using Termux's prebuilt packages. Canvas builds its own prefix inside its own data folder for user 10 and sets GIMP's relocation variables (GIMP3_DATADIR and friends) at launch, so it runs for the Googlebook's main account. Making it fully relocatable for extra accounts (users 11 and up) is on the Phase 2 list.
- **Why programs live in the native-library folder.** Android has refused to run program files from an app's writable storage since Android 10, and GIMP starts every plug-in as its own program. So every ELF file ships inside the APK as a `lib*.so` file, which Android unpacks to a read-only folder it allows code to run from.
- **Permissions.** No internet permission (GIMP's update check is compiled out; Help opens in the browser). Keyboard shortcuts such as Ctrl/Meta combos use Android 17's `CAPTURE_KEYBOARD`, a normal permission. lorie's optional accessibility key interceptor is removed, because it watches input device-wide and Play Protect blocks sideloaded apps that ask for accessibility. Files come through the question below.

**Benefits of route A**

- It is the real GIMP, the whole of it: every tool, filter, plug-in, script, file format and shortcut, not a lookalike.
- It runs at native speed. GIMP does its image work on the CPU (GEGL, multithreaded across all cores), so a native build is as fast as GIMP on Linux on the same chip. Nothing is emulated or virtualised.
- It works with a pen from day one: pressure, tilt and eraser reach GIMP the way a Wacom tablet does on Linux.
- It is one APK for every Googlebook. It works offline, needs no Terminal or VM, and installs like any other app.
- Every later step builds on it. The native-feel phases add to this base rather than replacing it, and if GIMP moves to GTK4 the build tree carries over to route C.

**Costs and risks**

- **Build size and time.** About 150 libraries × 2 ABIs. A cold CI build will take hours, and cached rebuilds of just GIMP or the app will take minutes.
- **APK size.** Krita's Android APKs are 168–179 MB per ABI. Canvas is likely 250–350 MB for one universal APK, measured in Phase 0. If that's too big, we ship split per-ABI APKs next to it.
- **3.2.0 → 3.2.6.** Termux's recipe stops at 3.2.0, so the patches have to be re-done on 3.2.6.
- **Python plug-ins need GObject introspection data made during a cross build.** Termux has a working method, and Script-Fu and the C plug-ins don't need it.
- **Android has no System V shared memory.** Use Termux's libandroid-shmem first; memfd later.
- **GTK3 scales only by whole numbers.** The HP's panel is 1.125×, so text is scaled through DPI instead, with icon sizes to match.
- **Android 17 limits app memory by device RAM.** Big images need GEGL's cache and swap tuned.
- **GIMP does not accept AI-assisted contributions upstream.** Canvas's patches stay in Canvas; only you can decide to report bugs to GIMP.

## Phases

### Phase 0: foundations and a first window

- Create the repo, the `./cv` helper, CI, and the signing key (kept outside git and backed up privately).
- Pin termux-packages and termux-x11. Set up a CI job that builds the GTK3 stack for arm64 with Canvas's app ID and paths, cached per ABI.
- Build the Android shell: an Activity with lorie's view, the unpacker (data plus the symlink tree), and the process launcher. Strip lorie's accessibility service, exported receivers, INTERNET permission and its TCP listener.
- Tooling: `tools/pack.py` turns the built prefix into APK native libraries and a data archive, and records the size of each part.
- **Done when:** `gtk3-demo` runs inside a Canvas APK on the HP, follows window resizes, and takes mouse and keyboard input. That proves the display, the build and the exec rules end to end.

### Phase 1: the raw port (GIMP 3.2.6 runs like on Linux) → v0.1

- **The full GIMP stack:** GIMP 3.2.6, GEGL 0.4.72, babl 0.1.128, and the file-format libraries (PNG, JPEG, TIFF, WebP, HEIF/AVIF, JPEG XL, OpenEXR, PDF import via poppler, PostScript via ghostscript, SVG via librsvg, PSD and XCF built in).
- **Plug-ins:** all of them, including Script-Fu and Python 3. The first launch registers them with a progress screen.
- **Input:**
  - Pen pressure, tilt and eraser.
  - Right-click, and scrolling with the wheel and trackpad.
  - Keyboard shortcuts with `CAPTURE_KEYBOARD`.
  - Text copy and paste between GIMP and Android.
- **Window:**
  - Single-window mode filling the Canvas window, following resizes live.
  - Text and icon sizes worked out from the screen's density.
  - Closing the window runs GIMP's own quit, so unsaved images still prompt.
- **Files:** per your answer on file access below. "Open with Canvas" from the Files app works in both options.
- **Both architectures:** arm64 tested on the HP, x86_64 on the ASUS, plus a CI smoke test in an x86_64 emulator.
- **Licences:** THIRD_PARTY_NOTICES, an in-app About & licences page, and full sources with every release.
- **Done when**, on both Googlebooks, you can do all of these:
  - Open a JPEG, PNG, PSD and XCF.
  - Paint with pressure.
  - Add a non-destructive filter.
  - Run a Script-Fu and a Python plug-in.
  - Export PNG and JPEG.
  - Quit and restart with your settings kept.

### Phase 2: at home on a Googlebook

- **Look:**
  - A Canvas theme for GIMP that follows the system's light or dark setting, uses the system fonts, and colours the caption bar to match.
  - Crisp icons on high-density screens.
- **Files and sharing:**
  - Android's own picker for Open, Save and Export (GIMP 3.4 moves to native file choosers upstream, which helps).
  - Share and "Export to…" into other apps.
  - Recent files and launcher shortcuts ("New image", "Paste as new image").
- **Clipboard, drag-and-drop and print:**
  - Images on the clipboard in both directions.
  - Dragging images from the Files app onto the canvas.
  - Printing through Android's print dialog.
- **Trackpad:** pinch to zoom, two-finger pan and rotate on the canvas.
- **Speed and robustness:**
  - A fast start: plug-in and font caches ready at install time.
  - Memory tuned to Android 17's app limits.
  - Autosave of unsaved work when Android closes the app.
- **Keyboard shortcut helper** (Meta + /) listing GIMP's own shortcuts.
- **Full relocation,** so second accounts on the same Googlebook work.

### Phase 3: many windows, better pen

- GIMP's dialogs and multi-window mode as real Googlebook windows, each with its own taskbar entry and snapping. This works by giving each X window its own Android window, as the Breadstick xserver prototype does. An alternative is GTK3's Wayland backend with a small built-in compositor.
- Lower pen latency, pen barrel-button mapping, and a pen settings page.
- Speed: SIMD for arm64 hot paths (upstream has none yet), and GPU presentation.

### Phase 4: follow upstream

- Rebase onto GIMP 3.4 when it ships: the new project format and native file choosers.
- When GIMP moves to GTK4, switch the display to GTK4's own Android backend (route C): real Android windows, the Android file picker and IME with no X server in between.

## Repos, builds and uploads

| What | Where |
|---|---|
| Source | `~/canvas` → github.com/kuscher/canvas (private until you flip it) |
| Helper | `./cv`: `ci` (start a build), `fetch` (download and sign the APK), `install`, `start`, `logs`, `shot`, `release [--publish]` |
| CI | GitHub Actions on ubuntu-24.04 x86_64. Jobs: deps (per ABI, cached by recipe hash), GIMP, APK (Gradle + lorie's CMake), and an x86_64 emulator smoke test. CI produces an unsigned APK. |
| Signing | Release key in `~/.config/canvas/keystore.jks` + `keystore.pass`, never in git. The keystore is backed up to private storage (folder a private folder with a README); the password goes in your password manager. |
| Releases | GitHub releases with: `Canvas.apk` (stable name, so `releases/latest/download/Canvas.apk` always works); `Canvas-<v>-source.tar.gz` (this repo, the patches, and the pinned upstream revisions); `Canvas-<v>-third-party-sources.tar` (every upstream source tarball the APK is built from, as the GPL asks); `SHA256SUMS`; notes from `CHANGELOG.md` |
| README | In the style of DiscoBar: icon, "⬇ Download Canvas.apk", screenshots, and a step-by-step install. It says clearly that Canvas is GIMP, made by the GIMP team and built here for Googlebooks, and that it is not made or endorsed by them. It also carries the passion-project note, "Made on a Googlebook", and a tiny Claude credit. |
| Device | Each build goes into the HP's Download folder and is installed over adb. The ASUS covers x86_64. |
| Licences | Own code (Android shell, tools, build scripts): MIT. Patches to upstream projects: their own licences. The APK as a whole: GPL-3.0-or-later, because GIMP and lorie are GPL-3.0. Notices, licence texts and full sources ship with every release. |

## Questions

1. **The name.** "Canvas" collides with the following:
   - A live US trademark, CANVAS, for graphic-design software (Canvas GFX, class 9).
   - Google's own "Chrome Canvas" drawing app on Chromebooks.
   - Instructure's Canvas (education, 10M+ Android installs).
   - Drawing apps on Play literally called "Canvas".

   The options are to keep it, to use "CanvasBook" (your Book family, and distinct from Chrome Canvas), or to have me find three clean names.
2. **Repo visibility.** A public repo gets free, unmetered 4-core CI runners. A private one gets 2-core runners and 2,000–3,000 minutes a month, which a few cold builds of this size would use up. Public from the first push, or private for now?
3. **File access in the raw port.** One option is "All files access", switched on once in Settings, so GIMP's own Open and Export dialogs see Pictures, Downloads and Documents directly. The other is no broad access: only files opened with Canvas and Canvas's own folder until Android's picker lands in Phase 2.
4. **Scope of the go-ahead.** Phase 0 and then Phase 1 through to a v0.1 release, checking in only for decisions? Or Phase 0 first, then a check-in?
