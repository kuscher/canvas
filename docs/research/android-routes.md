<!-- Research agent report, 2026-09-29. [V] = verified at the linked URL, [I] = inference. -->

# GIMP 3.x as a Googlebook APK: display-route research (as of 2026-09-29)

**Bottom line:** Route A (native bionic GIMP 3.2 with the termux-x11 "lorie" X server embedded in the APK) is the only route that meets all the constraints today. termux-x11 was refactored into an embeddable Android library in July 2026, and it already forwards stylus pressure, tilt and eraser. Termux's own recipes show GIMP 3.2 builds for bionic.

Tags: **[V]** means verified at the linked URL (source code, docs or repo metadata). **[I]** means my inference. Device facts from you: HSUM on (`ro.fw.mu.headless_system_user=true`), users 0 (headless system user) and 10 (the human), arm64-v8a, 4 KB pages, SDK 37, 1920x1200 at 180 dpi, FEATURE_PC, freeform windows.

---

## 1. Termux:X11 (lorie)

**Status and license**
- GPL-3.0, about 4.9k stars, last push 2026-09-28. Version string is `1.03.01-<commit>-<date>`, shipped only through the `nightly` release tag [V] ([repo](https://github.com/termux/termux-x11), [version.gradle](https://github.com/termux/termux-x11/blob/master/lorie/version.gradle), [nightly](https://github.com/termux/termux-x11/releases/tag/nightly)).
- Requires Android 8+ [V] (README).

**Architecture** [V]
- The X server is `CmdEntryPoint.main()`. The Termux `termux-x11` script / shell-loader starts it through `app_process`, and the loader checks the APK signature before loading its classes ([Loader.java](https://github.com/termux/termux-x11/blob/master/shell-loader/src/main/java/com/termux/x11/Loader.java)). It runs as a separate process named `termux-x11` ([README](https://github.com/termux/termux-x11)).
- The server sends an `ACTION_START` broadcast carrying a Binder to the app. The Activity then gets a `socketpair` fd back as a `ParcelFileDescriptor` over AIDL ([CmdEntryPoint.java](https://github.com/termux/termux-x11/blob/master/lorie/src/main/java/com/termux/x11/CmdEntryPoint.java), [cmdentrypoint.cpp](https://github.com/termux/termux-x11/blob/master/lorie/src/main/cpp/lorie/cmdentrypoint.cpp)).
- Framebuffers are **AHardwareBuffers** allocated CPU-read/write and GPU-sampled. Their handles cross the socket with `AHardwareBuffer_sendHandleToUnixSocket`. The Activity wraps them as `EGLImage` and draws them into a **SurfaceView** ([buffer.c](https://github.com/termux/termux-x11/blob/master/lorie/src/main/cpp/lorie/buffer.c), [LorieView.java](https://github.com/termux/termux-x11/blob/master/lorie/src/main/java/com/termux/x11/LorieView.java)).
- Core X drawing uses `fb` (software). DRI3 and Present are implemented, including GPU offload of Present copies (PR #1053, merged 2026-07) and `DRM_FORMAT_MOD_LINEAR` (PR #1000) ([InitOutput.c](https://github.com/termux/termux-x11/blob/master/lorie/src/main/cpp/lorie/InitOutput.c)).
- Clients connect through `$TMPDIR/.X11-unix/X<n>`; TMPDIR is mandatory. The server also opens a TCP "knock" listener bound to `INADDR_ANY`. That should be removed or bound to loopback when embedding [V code / I advice].

**Display**
- Resolution modes are `native`, `scaled` (%), `exact` and `custom` [V LorieView].
- In `native` mode each `surfaceChanged` becomes an `RRScreenSizeSet`, so the X screen follows freeform window resizes [V InitOutput.c].
- DPI defaults to 96; override with `-dpi` [V]. GTK3 on X11 only scales by integers (`GDK_SCALE`). On your 180 dpi panel, set Xft/`-dpi` to about 108 instead [I].

**Stylus** [V]
- lorie creates separate XInput pen and "TERMUX-X11 ERASER" devices. Axes: pressure 0–65535, tilt X/Y ±64, rotation/wheel, using the xf86-input-wacom ranges ([InitInput.c](https://github.com/termux/termux-x11/blob/master/lorie/src/main/cpp/lorie/InitInput.c)).
- They are enabled when an InputDevice reports `SOURCE_STYLUS`. `TOOL_TYPE_STYLUS` and `TOOL_TYPE_ERASER` go to `sendStylusEvent` ([TouchInputHandler.java](https://github.com/termux/termux-x11/blob/master/lorie/src/main/java/com/termux/x11/input/TouchInputHandler.java)).
- A 2024 bug where GIMP saw the device but no pressure ([#634](https://github.com/termux/termux-x11/issues/634)) predates this code.

**Mouse and touchpad** [V]
- Touchpad-emulation and simulated-touch gesture modes: two-finger tap = right click, two-finger swipe = scroll.
- Real mouse/touchpad scrolling comes from `AXIS_VSCROLL`/`AXIS_HSCROLL`. There is an optional `requestPointerCapture` preference and DeX-specific handling.
- Pinch zooms lorie's own viewport; it is not forwarded to X clients.

**Keyboard** [V]
- `preferScancodes`, and **Meta capture only through an optional AccessibilityService** (`KeyInterceptor`) or Samsung DeX meta capture ([manifest](https://github.com/termux/termux-x11/blob/master/lorie/src/main/AndroidManifest.xml)).
- It does not use Android's `CAPTURE_KEYBOARD` (see §7).

**Clipboard:** text only, as `STRING`/`UTF8_STRING`/`TEXT` targets. No images [V] ([clipboard.c](https://github.com/termux/termux-x11/blob/master/lorie/src/main/cpp/lorie/clipboard.c)).

**Drag and drop:** there is no Android→XDND bridge. The only `OnDragListener` moves an on-screen overlay [V MainActivity.java].

**Desktop-mode issues**
- The caption bar overlapped the X screen in desktop mode; fixed July 2026 ([#984](https://github.com/termux/termux-x11/issues/984), fix "give up the space taken by the desktop windowing caption").
- Chromebook black screen, worked around with `-legacy-drawing` ([#514](https://github.com/termux/termux-x11/issues/514)).
- On one-Android-window-per-X-window, the maintainer says Android gives no API to create or control windows or decorations, so he won't implement it ([#715](https://github.com/termux/termux-x11/issues/715), [#953](https://github.com/termux/termux-x11/issues/953)).

**Embedding** [V]
- 2026-07-24: the Gradle module was split into **`:lorie` (a `com.android.library`) plus a thin `:lorie-app`** ([commit 37ad5a7](https://github.com/termux/termux-x11/commit/37ad5a7e13)).
- `:lorie` takes `APPLICATION_ID` from whichever host app depends on it. Resources were renamed to avoid collisions with the host. `version.gradle` sets NDK `29.0.14206865` and says host apps integrating `:lorie` should reuse it ([lorie/build.gradle](https://github.com/termux/termux-x11/blob/master/lorie/build.gradle)).
- Termux itself shipped a CI-built "Termux with Termux:X11 embedded" APK, to escape background cpuset throttling ([commit 0104970](https://github.com/termux/termux-x11/commit/0104970477)). It was dropped 2026-07-30 as "not sustainable against termux-app and its forks" ([commit 7122284](https://github.com/termux/termux-x11/commit/7122284058)).
- Build: Gradle + CMake 3.22. xserver, pixman, libxfont, xkbcomp, xorgproto, libtirpc etc. are vendored as git submodules and built with custom `recipes/*.cmake`. Needs Python3, bison and `patch` at build time.
- The library manifest merges in the a11y `KeyInterceptor`, exported receivers, `WRITE_SECURE_SETTINGS` and `INTERNET`. Strip them with `tools:node="remove"` [I].
- Other embedders:
  - **Breadstick-io/xserver** (created 2026-06, 0 stars, GPL-3.0): runs `libXlorie.so` **in-process** with a *rootless per-window pipeline* that Composite-redirects each top-level window to its own AHardwareBuffer and shows each as a separate DeX window ([repo](https://github.com/Breadstick-io/xserver)).
  - **Winlator** uses its own pure-Java X server, not lorie ([DeepWiki](https://deepwiki.com/brunodev85/winlator-app)) [V-secondary].
  - **Mobox** needs the separately installed Termux + Termux:X11 ([repo](https://github.com/olegos2/mobox)).
  - UserLAnd and Andronix historically use VNC or XSDL [I].

## 2. Termux on user 10 and HSUM

- **Termux refuses to bootstrap on non-primary users.** `TermuxInstaller` calls `PackageUtils.isCurrentUserThePrimaryUser()`, which is `userId == 0`. The error string says bootstrap binaries have a hardcoded `$PREFIX` and cannot be installed under any path other than `/data/data/com.termux/files/usr` [V] ([TermuxInstaller.java](https://github.com/termux/termux-app/blob/master/app/src/main/java/com/termux/app/TermuxInstaller.java), [PackageUtils.java](https://github.com/termux/termux-app/blob/master/termux-shared/src/main/java/com/termux/shared/android/PackageUtils.java), [FS-layout wiki](https://github.com/termux/termux-packages/wiki/Termux-file-system-layout)).
  - On the Googlebook the human is user 10, so stock Termux, and with it the usual Termux:X11 workflow, is unusable [I, near certain].
- **What `/data/data` is for a user-10 app:**
  - Zygote `isolateAppData()` mounts an empty tmpfs (`uid=0,gid=0,mode=0751`, `MS_NOEXEC`) over `/data/data`, `/data/user` and `/data/user_de`, and symlinks `/data/user/0 → /data/data`.
  - For userId ≠ 0 it bind-mounts the app's CE dir only at **`/data/user/10/<pkg>`**. So `/data/data/<pkg>` does not exist, even for your own package [V] ([Zygote.cpp](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/jni/com_android_internal_os_Zygote.cpp)).
  - Isolation is on by default (`persist.zygote.app_data_isolation` defaults to true) [V] ([ProcessList.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/services/core/java/com/android/server/am/ProcessList.java)).
- `termux-packages` accepts `TERMUX_APP__DATA_DIR=/data/user/<id>/<pkg>` (regex `^((/data/data)|(/data/user/[0-9]+)|…)/[^/]+$`) [V] ([properties.sh](https://github.com/termux/termux-packages/blob/master/scripts/properties.sh)). That hardcodes user 10, and a second human account would be user 11 [I].
- **HSUM:** `ro.fw.mu.headless_system_user=true` means user 0 always runs in the background and humans are later users (≥10) [V] ([AOSP multi-user](https://source.android.com/docs/devices/admin/multi-user)). I found no public Google document saying Googlebook OS uses HSUM; your device property is the evidence. termux-x11's `am start --user 0` docs therefore don't apply [I].

## 3. W^X, exec, targetSdk, 16 KB pages, verification

**exec from data dirs**
- Apps targeting API 29+ "cannot invoke `execve()` directly on files within the app's home directory." Android recommends loading only code embedded in the APK [V] ([Android 10](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)).
- Enforcement is SELinux `neverallow … app_data_file:file execute_no_trans`, except for the `untrusted_app_25`/`_27` domains (targetSdk ≤ 28) [V] ([app_neverallows.te](https://github.com/LineageOS/android_system_sepolicy/blob/lineage-23.2/private/app_neverallows.te), an AOSP mirror, [untrusted_app_27.te](https://github.com/LineageOS/android_system_sepolicy/blob/lineage-23.2/private/untrusted_app_27.te)).

**dlopen and mmap from data dirs are still allowed**
- `allow untrusted_app_all app_data_file:file { r_file_perms execute }` (audited), plus `system_linker_exec execute_no_trans`. That is why the `linker64 /path/elf` trick works [V] ([untrusted_app_all.te](https://github.com/LineageOS/android_system_sepolicy/blob/lineage-23.2/private/untrusted_app_all.te)).
- New in Android 17 for **targetSdk 37**: native files loaded with `System.load()` must be read-only, otherwise `UnsatisfiedLinkError` [V] ([Android 17 target changes](https://developer.android.com/about/versions/17/behavior-changes-17)).

**nativeLibraryDir is the accepted workaround**
- `allow appdomain apk_data_file:file { … map x_file_perms }`: files under `/data/app/…/lib/<abi>/` can be exec'd [V] ([app.te](https://github.com/LineageOS/android_system_sepolicy/blob/lineage-23.2/private/app.te); agnostic-apollo in [termux-app#2155](https://github.com/termux/termux-app/issues/2155); [termux-app#1072](https://github.com/termux/termux-app/issues/1072)).
- You need `useLegacyPackaging true` (extractNativeLibs) so the `lib*.so` files exist on disk [I; standard].
- A symlink in the data dir pointing into nativeLibraryDir should exec. The kernel resolves the target and SELinux checks the target's `apk_data_file` label [I, consistent with the policy].
- Termux itself stays at **targetSdk 28** to keep exec'ing from `$PREFIX` [V] ([gradle.properties](https://github.com/termux/termux-app/blob/master/gradle.properties)).

**Why this matters for GIMP:** GIMP starts every plug-in as a **separate process** (`gimp_spawn_async` with a pair of pipes) [V] ([gimpplugin.c](https://gitlab.gnome.org/GNOME/gimp/-/blob/master/app/plug-in/gimpplugin.c)). Every plug-in and interpreter binary (python3, Script-Fu) must therefore be in nativeLibraryDir as `lib*.so`, or run through `linker64`.

**Minimum installable targetSdk**
- Android 14 blocks targetSdk < 23 ([docs](https://developer.android.com/about/versions/14/behavior-changes-all#minimum-target-api-level)). Android 15 and 16 block < 24. Android 17 was "not yet confirmed" as of March 2026 [V] ([Bayton matrix](https://bayton.org/android/android-minimum-targetsdk-matrix/)).
- The Android 17 behavior-change pages list no new floor [V].
- Bypass: `adb install --bypass-low-target-sdk-block`.

**Other Android 17 changes**
- RAM-based **app memory limits** (exit description `MemoryLimiter:AnonSwap`; `am memory-limiter` to test) [V] ([all-apps changes](https://developer.android.com/about/versions/17/behavior-changes-all)). This is relevant for large GIMP images.
- For targetSdk 37, the large-screen opt-out from resizability and orientation rules is removed [V].

**16 KB pages** [V] ([page-sizes](https://developer.android.com/guide/practices/page-sizes))
- Applies to 64-bit ABIs (arm64 and x86_64). NDK r28+ aligns to 16 KB by default.
- Android 16+ has a 4 KB back-compat mode, with `android:pageSizeCompat`. On Android 17 it can be set to fatal.
- Play requirement starts 2027-02-01.
- Your device uses 4 KB pages, so there is no runtime impact today. Still build 16 KB-aligned, since termux-x11 already uses NDK 29 [I].

**Sideloading and verification**
- Developer verification starts 2026-09-30 in BR, ID, SG and TH, and goes global in 2027 [V] ([9to5Google](https://9to5google.com/2026/03/19/android-advanced-flow-sideloading/), [Aug rollout](https://9to5google.com/2026/08/18/google-gradually-rolling-out-androids-advanced-sideloading-ahead-of-developer-verification/)).
- The advanced flow: developer mode, a coercion check, a restart, a 1-day wait, then biometric/PIN [V]. Free limited-distribution accounts cover up to 20 devices [V] ([Android Developers Blog](https://android-developers.googleblog.com/2026/03/android-developer-verification.html)).
- Play Protect's enhanced fraud protection is now in **185 markets**. It blocks internet-sideloaded apps that request **Accessibility**, SMS or notification-listener permissions [V] ([Google blog](https://blog.google/security/keeping-google-play-android-app-ecosystem-safe-2025/)). So do **not** ship lorie's a11y `KeyInterceptor`.
- I found no size cap for sideloaded APKs [I].

**Phantom process killer**
- Since Android 12, child processes are killed above a limit of 32 across all apps [V] ([issuetracker 205156966](https://issuetracker.google.com/issues/205156966), [agnostic-apollo doc](https://github.com/agnostic-apollo/Android-Docs/blob/master/en/docs/apps/processes/phantom-cached-and-empty-processes.md)).
- It matters for GIMP's plug-in processes mainly when the app is in the background [I].

## 4. GTK Android options

**(a) GTK4 Android backend: merged upstream**
- Introduced in **GTK 4.17.4 (2025-02-01)**: "new backend… still considered experimental", with a CI job producing a signed gtk4-demo APK (MR [!7555](https://gitlab.gnome.org/GNOME/gtk/-/merge_requests/7555)). First stable release was 4.18 [V] ([NEWS](https://gitlab.gnome.org/GNOME/gtk/-/blob/main/NEWS)).
- Later work: GL renderer (4.18), multitouch plus initial IME (4.19.1), Adreno fixes and GIRs (4.19.3), Choreographer vsync (4.23.x), accent colour (4.24.0, 2026-09-11) [V].
- Features in [gdk/android](https://gitlab.gnome.org/GNOME/gtk/-/tree/main/gdk/android) [V]:
  - **Each toplevel spawns its own Activity** with `FLAG_ACTIVITY_NEW_TASK|MULTIPLE_TASK`, which suits freeform windows (`gdkandroidtoplevel.c`).
  - Stylus and eraser tools with pressure, distance and tilt; the tilt maths was "taken from Termux-x11" (`gdkandroidseat.c`).
  - Clipboard through ClipData with arbitrary MIME types.
  - DnD (`gdkandroiddnd.c`), and `content://` URIs as GFile (`gdkandroidcontentfile.c`).
  - `gtkfilechoosernativeandroid.c` (SAF picker) and `gtkimcontextandroid.c`.
- Build tool: **Pixiewood / gtk-android-builder** (GPL-3.0, 109 stars, pushed 2026-09-18). It needs meson ≥1.9 with `android_exe_type: 'application'`, builds aarch64 and x86_64, and has `prepare/generate/build` steps [V] ([repo](https://github.com/sp1ritCS/gtk-android-builder)).
- **GIMP is GTK3.** 3.2.6 (2026-09-10) only begins "early preparations toward an eventual GTK4 port" [V] ([GIMP news](https://www.gimp.org/news/2026/09/10/gimp-3-2-6-released/), [Phoronix](https://www.phoronix.com/news/GIMP-3.2.6-Released)). The [roadmap](https://developer.gimp.org/core/roadmap/) has no GTK4 date.

**(b) GTK3:** no Android backend. The GDK backends on `gtk-3-24` are broadway, quartz, wayland, win32 and x11 [V] ([tree](https://gitlab.gnome.org/GNOME/gtk/-/tree/gtk-3-24/gdk)).

**(c) GTK3 Broadway** ([docs](https://docs.gtk.org/gtk3/broadway.html)) [V]
- The server sends zlib-compressed buffer diffs; JS inflates them into canvas `putImageData`, all on the CPU ([broadway.js](https://gitlab.gnome.org/GNOME/gtk/-/blob/gtk-3-24/gdk/broadway/broadway.js)).
- No pressure: the JS never reads `pressure` or `pointerType`.
- Clipboard is not implemented ("convert_selection not implemented", [gdkselection-broadway.c](https://gitlab.gnome.org/GNOME/gtk/-/blob/gtk-3-24/gdk/broadway/gdkselection-broadway.c)).
- The [TODO](https://gitlab.gnome.org/GNOME/gtk/-/blob/gtk-3-24/gdk/broadway/TODO.broadway) lists "Add resize handling to js WM", "keyboard focus handling" and "rgba support".
- GTK4 deprecated the Broadway renderer in 4.19.3.
- It could run in a WebView over `ws://127.0.0.1`, but every window becomes a canvas inside one page [I].

**(d) GTK3 Wayland plus an Android-side compositor**
- GTK3 has a mature Wayland backend (tablet-v2, data-device). Prior-art compositors are all young:
  - **TAWC**: Smithay compositor + XWayland + tawcroot + libhybris; GPL-3.0; 230 stars; created 2026-06, active. It runs glibc distros, not bionic, and "Linux apps… alongside Android apps in the app switcher" [V] ([repo](https://github.com/wmww/tawc)).
  - **ALR**: MIT, 14 stars. Its goal is non-root glibc GUI apps "(GIMP)" through an in-app Wayland compositor, but it is still at PoC planning [V] ([repo](https://github.com/Meapri/android-on-linux)).
  - [wlroots-android-bridge](https://github.com/Xtr126/wlroots-android-bridge): AHardwareBuffer allocator and `ASurfaceTransaction` per window; 53 stars; last push 2026-01.
  - [WayLandIE](https://github.com/AstroCODEsky/WayLandIE): one-day repo.
  - `anland`: root only.

## 5. Cross-compiling the GNOME stack

**termux-packages**
- Recommended builder is the Docker image `ghcr.io/termux/package-builder` via `scripts/run-docker.sh` [V] ([wiki](https://github.com/termux/termux-packages/wiki/Build-environment)).
- Toolchain: NDK clang at API 24, `-Wl,-rpath=$PREFIX/lib -Wl,--enable-new-dtags` (DT_RUNPATH) [V] ([toolchain_30.sh](https://github.com/termux/termux-packages/blob/master/scripts/build/toolchain/termux_setup_toolchain_30.sh)).
- **GIR while cross-compiling:** `gi-cross-launcher` replays **pre-generated dump XMLs** committed per package version. Target binaries run under proot + `aosp-libs`, with qemu-user for arm [V] ([termux_setup_gir.sh](https://github.com/termux/termux-packages/blob/master/scripts/build/setup/termux_setup_gir.sh), [gi-cross-launcher.sh](https://github.com/termux/termux-packages/blob/master/packages/gobject-introspection/gi-cross-launcher.sh), [termux_setup_proot.sh](https://github.com/termux/termux-packages/blob/master/scripts/build/setup/termux_setup_proot.sh)).
- **GIMP 3.2.0 is already packaged** ([x11-packages/gimp/build.sh](https://github.com/termux/termux-packages/blob/master/x11-packages/gimp/build.sh)) [V]:
  - Options include `-Dcan-crosscompile-gir=true`, openmp/lua/javascript off, and linking `-landroid-shmem`, since bionic has no SysV shm.
  - The splash image is extracted from the official AppImage because running GIMP while cross-compiling is hard.
  - Dependency list: babl, gegl, gtk3, poppler, ghostscript, libheif, libjxl, openexr, librsvg, libmypaint, pygobject and others.
- Custom package name: `TERMUX_APP__PACKAGE_NAME` / `TERMUX_APP__DATA_DIR` in `properties.sh` (keep the name ≤21 characters because of socket-path and shebang limits) [V]. Forking requires rebuilding bootstrap and all packages [V] ([termux-app#forking](https://github.com/termux/termux-app#forking)).
- **Termux's prebuilt debs are unusable as-is.** All FHS paths are patched to `/data/data/com.termux/files/usr` at build time [V wiki], and RUNPATH is hardcoded [V], so they break under user 10.
- Better approach: reuse the recipes with your own prefix, plus runtime env overrides (`XDG_DATA_DIRS`, `GDK_PIXBUF_MODULE_FILE`, `FONTCONFIG_FILE`, `GI_TYPELIB_PATH`, `BABL_PATH`/`GEGL_PATH`, `GIMP3_*DIR`). GIMP has a `relocatable-bundle` meson option [V] ([meson_options.txt](https://gitlab.gnome.org/GNOME/gimp/-/blob/master/meson_options.txt)).
- Build time: I found no published figure. My estimate is a few CPU-hours per ABI for roughly 150 dependencies, which fits GitHub Actions only with caching and split jobs, as MixxxBook does [I].

**Alternatives**
- **Cerbero** has glib, cairo, pango, gdk-pixbuf, librsvg, gobject-introspection and **gtk 4.20.3** recipes (x11/wayland/broadway off). There is no GTK3 recipe and no GIMP-specific deps such as GEGL or babl [V] ([recipes](https://gitlab.freedesktop.org/gstreamer/cerbero/-/tree/main/recipes)).
- **Pixiewood** targets GTK4 apps only.

## 6. Prior art

- **pelya's XServer-XSDL "GIMP-Inkscape" APKs** (GIMP 2.8, around 2013–15): the closest match to Route A. It was a standalone APK with an SDL-based X server and a bundled userland, described as "fully working but very slow" [V-secondary] ([SourceForge](https://sourceforge.net/projects/libsdl-android/files/apk/XServer-XSDL/), [xserver-xsdl](https://github.com/pelya/xserver-xsdl)).
- **Winlator** (LGPL-2.1, 19k stars): one APK containing a Java X server, Box64, Wine and a glibc rootfs, shipped on GitHub releases [V] ([repo](https://github.com/brunodev85/winlator)). It proves large single-APK Linux GUI stacks work when sideloaded.
- **Krita (Qt)**
  - The Android/ChromeOS build is the "full desktop version… doesn't have a special touch user interface", tablets and Chromebooks only, beta [V] ([2020 post](https://krita.org/en/posts/2020/first-krita-beta-for-android-and-chromeos-in-play-store/), [5.3.1.1](https://krita.org/en/posts/2026/krita-5.3.1.1-released/)).
  - The maintainer spent "almost all of his time keeping Krita running on Android" instead of building a tablet UI [V] ([2024 roadmap](https://krita.org/en/posts/2024/2024-roadmap/)).
  - 5.3.4 APKs are **168 MB (arm64) and 179 MB (x86_64)** [V] ([download.kde.org](https://download.kde.org/stable/krita/5.3.4/)).
  - Files go through Qt's content-URI file engine / SAF [V] ([Qt blog](https://www.qt.io/blog/qt-for-android-storage-updates)).
  - It needs per-OEM stylus workarounds (Xiaomi, OnePlus) [V].
  - Users complain about small, mouse-sized targets [V] ([krita-artists](https://krita-artists.org/t/krita-android-ui-feedback-and-suggestions/74677)).
  - Lessons: a desktop UI is acceptable on a laptop, but expect around 170 MB per ABI and ongoing maintenance of the platform glue [I].
- **Your own ports:** MixxxBook and OfficeBook are the same pattern of a desktop toolkit inside an APK with a desktop UI.

## 7. Android desktop input and storage APIs

- **Stylus:**
  - `TOOL_TYPE_ERASER` (API 14), `getPressure()`, `AXIS_TILT`, `AXIS_ORIENTATION`; palm rejection via `FLAG_CANCELED` [V] ([input compat](https://developer.android.com/develop/ui/views/touch-and-input/input-compatibility-on-large-screens), [MotionEvent](https://developer.android.com/reference/android/view/MotionEvent)).
  - Low-latency front-buffer rendering: androidx.graphics `GLFrontBufferedRenderer` and motion prediction [V] ([advanced stylus](https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/advanced-stylus-features)). This can't reach inside an X11 canvas; at most it could draw a "wet ink" overlay [I].
- **Touchpad:**
  - `CLASSIFICATION_TWO_FINGER_SWIPE`, `CLASSIFICATION_PINCH`, `AXIS_GESTURE_PINCH_SCALE_FACTOR` and `AXIS_GESTURE_SCROLL_*` (API 34) [V].
  - Android 17: under pointer capture, touchpads report like a captured mouse (relative movement plus scroll). `requestPointerCapture(POINTER_CAPTURE_MODE_ABSOLUTE)` (API 37) gives raw finger positions [V] ([View](https://developer.android.com/reference/android/view/View#requestPointerCapture(int))).
  - GTK3 on X11 gets no pinch events, so map pinch to Ctrl+wheel for GIMP zoom [I].
- **Keyboard:**
  - **`android.permission.CAPTURE_KEYBOARD`** (added 36.1, protection level *normal*) plus `WindowManager.LayoutParams.setKeyboardCaptureEnabled(boolean)` lets the focused window receive system-shortcut keys. Home, Overview and Power are never delivered, and long-press Esc escapes capture [V] ([Manifest.permission](https://developer.android.com/reference/android/Manifest.permission#CAPTURE_KEYBOARD)). This replaces lorie's a11y interceptor.
  - Keyboard Shortcuts Helper: `onProvideKeyboardShortcuts`, Meta+/ (API 24+) [V] ([docs](https://developer.android.com/develop/ui/compose/touch-input/keyboard-input/keyboard-shortcuts-helper)).
- **Right-click, hover, DnD:** `setOnContextClickListener`, hover pointer icons, `DRAG_FLAG_GLOBAL` + `requestDragAndDropPermissions()` for drops from other apps [V] ([input compat](https://developer.android.com/develop/ui/views/touch-and-input/input-compatibility-on-large-screens)).
- **Multi-instance:** `PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI` (API 35+) [V] ([docs](https://developer.android.com/develop/ui/views/layout/support-multi-window-mode)).
- **Storage:**
  - SAF + `takePersistableUriPermission` [V] ([docs](https://developer.android.com/training/data-storage/shared/documents-files)). The cap of 512 persisted grants since Android 11, with the oldest silently released, is from [CommonsWare](https://commonsware.com/blog/2020/06/13/count-your-saf-uri-permission-grants.html) [V-secondary].
  - `MANAGE_EXTERNAL_STORAGE` via `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION` gives direct path access to shared storage (not `Android/data`). The restriction is a Play policy, so sideloaded apps can use it [V] ([docs](https://developer.android.com/training/data-storage/manage-all-files)). For user 10 that is `/storage/emulated/10` [I].
  - Clipboard images: ClipData with a FileProvider `content://` URI and an image MIME type [I; standard API].

---

## Route comparison

| | **A. Native bionic GIMP + embedded lorie** | **B. GTK3 Broadway + WebView** | **C. GTK4 Android backend** | **D. GIMP in the Terminal VM** |
|---|---|---|---|---|
| **Feasible today** | Yes. `:lorie` is an embeddable library [V]; GIMP 3.2 builds for bionic in Termux [V]. | Technically yes. | **No.** GIMP has no GTK4 port; 3.2.6 is only prep [V]. | Yes (`apt install gimp`), but it is not an APK and not allowed under your constraints. |
| **Performance** | Near-native CPU; GIMP's canvas is CPU/GEGL anyway. Zero-copy AHB→SurfaceView. SysV shm needs a shim or `--no-shm` [I]. | Poor: zlib diffs decoded in JS onto canvas, CPU-bound at 1920x1200 [V design / I perf]. | Would be best (GL renderer, vsync) [V backend]. | Weston + Xwayland with **software rendering, gfxstream not enabled** on this device [your notes], plus VM display copy. |
| **Native feel** | Medium to good. One freeform window follows resizes via RandR [V]. Stylus pressure, tilt and eraser [V]. Needs: a tiny WM or forced maximize for GIMP single-window mode; `CAPTURE_KEYBOARD`; pinch → zoom; image clipboard; Android→XDND; file access via `MANAGE_EXTERNAL_STORAGE` or a portal bridge [I]. Per-window tasks possible later (Breadstick approach). | Poor. No pressure, no clipboard, JS window manager inside one page [V]. | Best: an Activity per toplevel, SAF chooser, IME, ClipData, stylus axes [V]. | Poor. Everything sits inside the Terminal's display window. VM stops when the Terminal closes; files limited to shared folders; stylus pass-through unknown [your notes + I]. |
| **Effort** | Large (weeks to months): own-prefix build tree for about 150 deps × 2 ABIs; plug-ins as `lib*.so` + symlink tree; lorie glue [I]. | Medium to get running, low ceiling. | Very large: an upstream multi-year GTK4 port first. | Trivial. |
| **Blockers** | Prefix relocation and user-10 paths; W^X for about 100 helper binaries; GPL-3.0 compliance (fine); APK likely about 150–250 MB per ABI like Krita [I]; strip the a11y service or Play Protect blocks installs [V]. | Broadway clipboard and pressure missing; GTK4 deprecated Broadway; Android 17 loopback permission [unverified]. | GIMP GTK4 port does not exist. | `MANAGE_VIRTUAL_MACHINE` is signature/preinstalled, so no APK can bundle a VM [your notes]; the "no VM" constraint. |

**Recommendation:** go with **A**, and keep the in-APK Wayland variant (A′, GTK3 Wayland backend with a TAWC-style compositor giving Activity-per-toplevel) as a later option. C becomes the long-term path once GIMP moves to GTK4 [I]. Concrete first steps for A:
- Vendor `:lorie` (NDK 29).
- Run the X server in-process or as an `app_process` child of the app, which also avoids background throttling [I].
- Delete `KeyInterceptor`, the exported receivers and the INADDR_ANY knock listener.
- Use `native` resolution mode at about 108 dpi.
- Build GIMP from Termux's recipes with your own prefix, `relocatable-bundle=yes` and `useLegacyPackaging true`, with plug-ins and python3 shipped in nativeLibraryDir.
- Add `CAPTURE_KEYBOARD`, a pinch → Ctrl+wheel mapping, a PNG clipboard target, and an SAF/`MANAGE_EXTERNAL_STORAGE` file bridge.
