<!-- Research agent report, 2026-09-29. Facts marked (V) were checked against the linked source; (I) is inference. -->

# GIMP upstream research for an Android/Googlebook port (as of 2026-09-29)

**Key:** facts marked *(V)* were checked today against the linked source, mostly the git tree at tag `GIMP_3_2_6` (cloned) or live pages. *(I)* marks my own inference. Several GitLab links go to the tag; the paths are exact.

---

## 1. Release history and current state

**Stable releases.** Dates are announcement dates; tag dates are in brackets where they differ. Tag list: https://gitlab.gnome.org/GNOME/gimp/-/tags *(V)*

| Version | Date | Source |
|---|---|---|
| 3.0.0 | 2025-03-16 [tag 03-17] | https://www.gimp.org/news/2025/03/16/gimp-3-0-released/ |
| 3.0.2 | 2025-03-23 | https://www.gimp.org/news/2025/03/23/gimp-3-0-2-released/ |
| 3.0.4 | 2025-05-18 | https://www.gimp.org/news/2025/05/18/gimp-3-0-4-released/ |
| 3.0.6 | 2025-10-06 [tag 10-05] | https://www.gimp.org/news/2025/10/06/gimp-3-0-6-released/ |
| 3.0.8 | 2026-01-24. Its notes say it "might be the final release in the GIMP 3.0 series" | https://www.gimp.org/news/2026/01/24/gimp-3-0-8-released/ |
| **3.2.0** | 2026-03-14 | https://www.gimp.org/news/2026/03/14/gimp-3-2-released/ |
| 3.2.2 | 2026-03-28 | https://www.gimp.org/news/2026/03/28/gimp-3-2-2-released/ |
| 3.2.4 | 2026-04-19 [tag 04-17] | https://www.gimp.org/news/2026/04/19/gimp-3-2-4-released/ |
| **3.2.6 (latest)** | 2026-09-10 | https://www.gimp.org/news/2026/09/10/gimp-3-2-6-released/ |

**Development series** *(V)*
- Past dev releases: 3.1.2 (2025-06-23), 3.1.4 (2025-09-01), then 3.2.0 RC1 (2025-11-17), RC2 (2025-12-15) and RC3 (2026-03-02).
- The current dev series is **3.3.x**. `master` is at version `3.3.1` (https://gitlab.gnome.org/GNOME/gimp/-/raw/master/meson.build).
- **No 3.3.x release exists yet.** The August 2026 update says "There is not an official 3.3.2 development release yet" and calls 3.3.2 "the first development release" (https://www.gimp.org/news/2026/08/16/dev-update-august-2026/).
- The latest dev release of any kind is therefore 3.2.0 RC3.

**Next stable** *(V)*
- It will be **GIMP 3.4**. The 3.2.6 news says "Work … has mainly and already shifted to the development series which will eventually lead to GIMP 3.4."
- 3.4 is planned to bring:
  - a new zipped-XML project format
  - filters on layer masks and editable gradients
  - MyPaint spectral blending
  - PSD descriptor import
  - native file choosers
  - SVG cursors
- **GIMP 3.6** is planned for animation and multi-page support.
- Roadmap: https://developer.gimp.org/core/roadmap/. The FAQ says the team does not announce dates.

**GTK4 / GIMP 4** *(V)*
- The 3.2.6 news says: "Even though we are not yet planning a GTK4 port, it doesn't hurt to start preparing for it!" (deprecated-API cleanup by Ondřej Míchal and Jehan).
- The roadmap has no GTK4 entry. No GTK4 branch exists among the 170 branches.
- "GIMP 4.x" appears only as the removal target for deprecated API (`gimp_quit()`, 3.2.4 notes).
- The FAQ entry "Why doesn't GIMP use GTK4?" says the team only just finished the GTK3 port and that GTK4 "will come someday" (https://www.gimp.org/docs/userfaq.html).

**2.10.x** *(V)*
- The last release is 2.10.38 (tag 2024-05-02). Its notes call it the "(possibly last) GIMP 2 stable release" and say "we are now stopping backporting features". A 2.10.40 was floated but never tagged (https://www.gimp.org/news/2024/05/05/gimp-2-10-38-released/).
- No formal EOL page was found. It is dead in practice *(I)*.

## 2. Features relevant to a laptop / touch / pen device

**3.0 (previous major; 3.0.8 is its final point release)**, from https://www.gimp.org/release-notes/gimp-3.0.html *(V)*
- Non-destructive (NDE) filters by default.
- "Much better UI scaling on HiDPI screens".
- Native Wayland support.
- Tablet support: hotplug, and tablet buttons can be mapped to actions on Wayland.
- CSS themes in Light, Gray and Dark.
- Multi-layer selection.
- Formats: JPEG XL, QOI, better PSD, and CMYK import/export.
- Plug-ins: Python 3 via GObject Introspection (GI) replaces Python-fu. Script-Fu, JavaScript, Lua and Vala are also supported. The API is stable for all of 3.x.

**3.2.x (latest)**, from https://www.gimp.org/release-notes/gimp-3.2.html *(V)*
- New layer types: link layers ("smart objects") and vector layers.
- MyPaint brushes v2: 20 new brushes, aware of zoom and rotation, plus a pressure "Gain" slider.
- Overwrite paint mode and on-canvas text improvements.
- A System colour scheme that follows the OS.
- Formats: APNG, JPEG 2000 export, PSB export, AVCI import, DDS BC7, SVG export and vector PDF export, Procreate/Krita palettes, Photoshop `.acv`/`.alv`/`.pat` files, and loading of compressed images.
- A GEGL Filter Browser and a `GimpDrawableFilter` API (111 new libgimp functions).
- 3.2.0 is the last release with 32-bit Windows builds.
- 3.2.4: marching ants are hidden while moving (performance) and a Wayland cursor fix.
- 3.2.6:
  - **stylus barrel-rotation** input, which also feeds MyPaint
  - much faster font loading
  - 28 CVEs fixed
  - gdk-pixbuf minimum raised to 2.32.0
  - a "GIMP SDK" (headers and libraries) shipped in every package
  - a native macOS build on GitLab CI

**Touch** *(V)*
- Two-finger zoom and rotate gestures work "only … using touchpad. Not directly in canvas with a touchscreen" (https://developer.gimp.org/core/specifications/graphic_tablets_support/).
- None of the four GSoC 2026 projects touch mobile or touch: shortcuts dialog, extensions site, fonts/OpenType, GEGL filters/PSD (https://www.gimp.org/news/2026/07/18/gsoc-2026-midpoint-progress/).

**Fractional scaling** *(I)*: GTK3 only does integer scaling, and fractional scaling comes from the compositor. GIMP's SVG cursor work, which targets HiDPI, is in 3.3 only.

**ARM64 and packaging** *(V)*
- 3.2.6 ships all of these:
  - AppImage, Flatpak and Snap for x86_64 and aarch64
  - a universal Windows installer covering x64 and ARM64
  - MS Store for x64 and ARM64 (now needs build 20348+)
  - macOS DMGs for Intel and Apple Silicon

  Source: the downloads list in the 3.2.6 post; also https://www.gimp.org/news/2026/05/20/gimp-msix-bump/.
- The Snap became official on 2025-10-17 after the Snapcrafters handed it over (https://www.gimp.org/news/2025/10/17/official-snap/).

**Relocatable bundles** *(V)*: `-Drelocatable-bundle` defaults to on for Windows and macOS and off for Linux. The AppImage and Snap builds force `yes`. Details in §3.

## 3. Build system and dependencies (GIMP 3.2.6)

Sources: https://gitlab.gnome.org/GNOME/gimp/-/blob/GIMP_3_2_6/meson.build, `meson_options.txt`, and `INSTALL.in` *(V)*. The build uses Meson ≥0.61.0.

**Hard requirements** (minimum versions):
- UI and text stack:
  - GTK+ 3 ≥3.24.0
  - GLib / GObject / GIO ≥2.70.0, plus gmodule and gio-unix
  - ATK ≥2.4.0
  - cairo ≥1.14.0
  - pango, pangocairo and pangoft2 ≥1.50.0
  - HarfBuzz ≥2.8.2
  - fontconfig ≥2.12.4
  - freetype2 ≥2.1.7
- Imaging core:
  - gdk-pixbuf ≥2.32.0
  - **babl ≥0.1.118 and GEGL ≥0.4.66**. GEGL must be built with `-Dcairo=enabled`; INSTALL asks for GIR in babl and GEGL.
  - lcms2 ≥2.8
- Painting and metadata:
  - libmypaint ≥1.5.0 and mypaint-brushes-2.0
  - exiv2 ≥0.27.4
  - gexiv2 ≥0.14.0 and <0.15.0
  - json-glib ≥1.2.6 (master: ≥1.8.0)
- Rendering and PDF:
  - librsvg ≥2.40.6
  - poppler-glib ≥0.69.0 and poppler-data ≥0.4.9
- Extensions infrastructure: appstream (libappstream) ≥0.16.1 and libarchive.
- File formats and compression: libpng ≥1.6.25, libjpeg(-turbo), libtiff ≥4.0.0, zlib, bzip2, liblzma ≥5.0.0.
- Runtime and system:
  - glib-networking. It is runtime-tested, and the test is skipped when cross-compiling.
  - shared-mime-info (when vector icons are on)
  - libdl on Linux
  - X11 libraries only if GTK3's `targets` include x11 (`x11_target = gtk3.get_variable('targets').contains('x11')`). A Wayland-only or custom GTK3 build avoids them.

**Optional features:** libheif ≥1.15.1, libjxl ≥0.7.0, libwebp ≥0.6.0 (with mux and demux), openjpeg ≥2.1.0, OpenEXR ≥1.6.1, ghostscript (libgs), cairo-pdf ≥1.12.2, libwmf ≥0.2.8, libmng, cfitsio, aalib, iso-codes, OpenMP, gudev, alsa, xcursor, xpm, libunwind, libbacktrace, vala, gjs, lua, and print. WebKit and TWAIN are marked unmaintained.

**Build machine requirements** *(V)*
- **Python ≥3.6 with PyGObject ≥3.0, pycairo and a GExiv2 typelib** is a hard `error()` if missing, including for cross builds. Perl, xsltproc and gettext ≥0.19.8 are also needed.
- **GObject Introspection:** yes, it is still required at build time.
  - `dependency('gobject-introspection-1.0')` and `find_program('g-ir-compiler')` are unconditional.
  - GIR generation is turned **off automatically when cross-compiling** unless `-Dcan-crosscompile-gir=true` (https://gitlab.gnome.org/GNOME/gimp/-/blob/GIMP_3_2_6/libgimp/meson.build).
  - It is also off for static builds.
  - Without GIR, Python, JS and Lua plug-ins cannot run. Script-Fu is C and is unaffected *(I)*.
- **Cross-compiling needs a runnable GIMP:** "When cross-compiling, the build requires either an exe_wrapper or a native GIMP installed". The gimp-data splash is rendered by running GIMP during the build.

**Relocation on Linux** *(V)*
- `relocatable-bundle` sets `ENABLE_RELOCATABLE_RESOURCES`.
- `libgimpbase/gimpreloc.c` (BinReloc) then finds the executable via `/proc/self/exe`; libraries use `/proc/self/maps`.
- The prefix is `dirname(dirname(exe))`, with special handling for `lib*` directories.
- Compile-time `PREFIX/…` paths in gimprc are rewritten at runtime (`gimp_path_runtime_fix`). The MyPaint brush directory becomes `${gimp_installation_dir}/share/mypaint-data/2.0/brushes`.
- Environment overrides, which must be **absolute paths** (`gimp_env_get_dir`, https://gitlab.gnome.org/GNOME/gimp/-/blob/GIMP_3_2_6/libgimpbase/gimpenv.c):
  - `GIMP3_DATADIR`, `GIMP3_SYSCONFDIR`, `GIMP3_PLUGINDIR`, `GIMP3_LOCALEDIR`
  - `GIMP3_CACHEDIR`, `GIMP3_TEMPDIR`
  - `GIMP3_DIRECTORY` for the user config directory. The `-Dgimpdir` build option changes the default.

**Plug-ins** *(V)*
- Each plug-in is a separate process. `app/plug-in/gimpplugin.c` creates two `pipe()`s and calls `gimp_spawn_async(prog, "-gimp", protocol, rfd, wfd, mode, stack-trace-mode)`. The protocol is GimpWire (`libgimpbase/gimpwire.c`).
- Pixel tiles move through shared memory. `-Dshmem-type` accepts `auto` (resolves to SysV `shmget` on Linux), `posix` (`shm_open`) or `none`.
- Discovery requires a regular file with `G_FILE_ATTRIBUTE_ACCESS_CAN_EXECUTE`, or a script handled by `interpreters/*.interp` (binfmt-style). Each plug-in lives at `plug-ins/<name>/<name>`. `environ/*.env` files set plug-in environment variables.
- Scale: 87 plug-ins in `plug-ins/common` alone, 35 plug-in directories, and 12 Python plug-ins, so well over 100 executables.

**Android consequences** *(I, with Android fact V)*
- Android 10+ forbids `execve()` on files in the app home directory (https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission). Plug-ins would have to ship as `lib*.so` executables in `nativeLibraryDir` (with `extractNativeLibs`) and be reached through symlinks, or be patched to run in-process.
- `/proc/self/exe` points to `app_process64`, so BinReloc would compute a wrong prefix. Plan to set every `GIMP3_*` variable explicitly.
- bionic has no usable SysV shm. Termux links `libandroid-shmem`. Otherwise use `shmem-type=none`, or patch in memfd/ASharedMemory.
- **GTK 3.24 has no Android GDK backend.** Its backends are broadway, quartz, wayland, win32 and x11 (https://gitlab.gnome.org/GNOME/gtk/-/tree/gtk-3-24/gdk). The only official Android backend is in GTK4 (`gdk/android` since 4.18.0; GTK 4.24.0 was tagged 2026-09-11; https://gitlab.gnome.org/GNOME/gtk/-/tree/main/gdk/android). A GTK3 port therefore needs an embedded Wayland or X11 server, Broadway, or a new GDK3 backend.

## 4. Upstream packaging scripts

All scripts are at https://gitlab.gnome.org/GNOME/gimp/-/tree/GIMP_3_2_6/build *(V)*

- **AppImage:** `build/linux/appimage/AppRun` and `3_dist-gimp-goappimage.sh`.
  - Requires a `relocatable-bundle=yes` build.
  - Bundles `ld-linux` for both x86_64 and aarch64.
  - AppRun sets `LD_PRELOAD` for libbabl, libgegl and libgimp, clears `LD_LIBRARY_PATH`, and sets `XDG_DATA_DIRS`.
  - `conf_app` appends to AppRun: `GIO_MODULE_DIR`, `GDK_PIXBUF_MODULEDIR`/`MODULE_FILE`, `GTK_PATH`, `GTK_IM_MODULE_FILE`, `LIBTHAI_DICTDIR`, `LIBHEIF_PLUGIN_PATH`, `GS_LIB`, `GI_TYPELIB_PATH`, `PYTHONDONTWRITEBYTECODE`.
  - A script comment notes that babl, GEGL and GIMP variables are "not needed when built relocatable".
- **Flatpak:** `build/linux/flatpak/` (`org.gimp.GIMP-nightly.json`, GNOME runtime). Not relocatable.
- **Snap:** `build/linux/snap/snapcraft.yaml`, with `-Drelocatable-bundle=yes`, arm64 and amd64.
- **macOS:** `build/macos/` (`1_build-deps-macports.sh`, `2_build-gimp-macports.sh`, `2_bundle-gimp-uni_base.py`, `dmg/3_dist-gimp-apple.sh`).
  - The install directory comes from `[NSBundle mainBundle]`.
  - Typelib `shared-library=` entries are rewritten to `@rpath`, and `.pc` prefixes to `${pcfiledir}`.
- **Windows:** `build/windows/` (MSYS2 `1_build-deps-msys2.ps1`, `2_build-gimp-msys2.ps1`, `2_bundle-gimp-uni_base.py`, Inno `installer/gimp-setup.iss` with ARM64 defines, and `store/`).
  - The install directory comes from `g_win32_get_package_installation_directory_of_module`.
  - `python.exe` is bundled and typelibs are gathered with `g-ir-inspect`.

## 5. babl and GEGL

**Versions** *(V)*
- Paired with 3.2.6: **babl 0.1.128** (2026-08-07) and **GEGL 0.4.72** (2026-09-07).
- Paired with 3.2.0: babl 0.1.124 and GEGL 0.4.68.
- NEWS files: https://gitlab.gnome.org/GNOME/babl/-/blob/BABL_0_1_128/NEWS and https://gitlab.gnome.org/GNOME/gegl/-/blob/GEGL_0_4_72/docs/NEWS.adoc.
- GEGL requires babl ≥0.1.116, GLib ≥2.44 and json-glib ≥1.0. Both use Meson ≥0.60.
- babl became relocatable in 0.1.118. Both libraries have a `relocatable-bundle` option. GEGL finds its ops via `GEGL_PATH` or `dladdr()`.

**SIMD** *(V)*
- **x86_64:** babl builds extension variants for x86-64-v2 (SSE4.2), v3 (AVX2/FMA/F16C) and v4 (AVX-512, added 0.1.120, with runtime checks hardened in 0.1.128), chosen at runtime. GEGL builds `gegl-common*-x86_64-v2` and `-v3` op modules and library variants.
- **aarch64:** only `-ftree-vectorize` on top of the baseline. There is no hand-written NEON variant; that exists only for 32-bit `arm`.
- **GIMP core:** SSE2/SSE4 layer-mode paths only. A July 2026 commit on branch `bruno/minimum-neon` says "Even if we do not do any neon acceleration right now…" (https://gitlab.gnome.org/GNOME/gimp/-/commits/bruno/minimum-neon).

**OpenCL** *(V)*: GEGL 0.4.72 updated to OpenCL 3.0 headers and loads via gmodule, but the path is "still disabled by default in GIMP". Re-enabling it is a "WIP?" item on the 3.4 roadmap.

**Threading** *(V)*: GEGL's thread count defaults to `g_get_num_processors()` with a maximum of 64 and can be overridden with `GEGL_THREADS`. There are also `GEGL_USE_OPENCL`, `GEGL_CACHE_SIZE`, `GEGL_SWAP` and `GEGL_TILE_SIZE` (https://gitlab.gnome.org/GNOME/gegl/-/blob/GEGL_0_4_72/gegl/gegl-init.c).

## 6. Existing GIMP-on-Android efforts

**Official stance** *(V)*: from the FAQ, "we'd rather focus on delivering a great image manipulation program for desktop users … We might release some day a GIMP-branded image manipulation program for Android. Though no promises!" There is no official Android build.

**Termux** *(V)*: https://github.com/termux/termux-packages/tree/master/x11-packages/gimp
- The package is **still at GIMP 3.2.0** (revision 4, last touched 2026-07-21). An in-tree patch note says updating to 3.2.1+ will need heavy rework.
- It is an X11 app that needs Termux:X11. It depends on `libandroid-shmem` (SysV shm emulation) and `libandroid-execinfo`.
- Flags: `-Dcan-crosscompile-gir=true -Dopenmp=disabled -Dlibunwind=false -Dlibbacktrace=false -Dvala=disabled -Djavascript=disabled -Dmng=disabled -Dwmf=disabled`.
- Build tricks:
  - It extracts the splash PNG from the official x86_64 AppImage instead of running GIMP during the build.
  - It runs tools under proot.
- Patches:
  - `meson.build.patch`: skips the shm and pango run-tests and installs a prebuilt splash
  - `treat-android-as-linux.patch`
  - `do-not-check-for-gi.patch`
  - `enable-script-fu-interpreter.patch`
- Related packages: babl 0.1.128 (`packages/babl`, which patches `/tmp` to the Termux prefix), GEGL 0.4.72 (`packages/gegl`), and GTK 3.24.52 with x11, wayland and broadway backends (`x11-packages/gtk3`).

**Play Store "GIMP"** *(V)*: `tech.ula.gimp` by UserLAnd Technologies (https://play.google.com/store/apps/details?id=tech.ula.gimp).
- $1.99, 50K+ downloads, updated 2026-05-29.
- It runs Linux GIMP in a proot distro over VNC with touch-to-mouse mapping, and says it is "not created by the main GIMP development team".
- GPLv3 source: https://github.com/CypherpunkArmory/gimp (last pushed 2025-09-05).
- The GIMP forum thread lists this app and the unrelated "Xgimp" (https://www.gimp-forum.net/Thread-GIMP-on-Android). An issue about the fake Xgimp app on Apple's App Store is https://gitlab.gnome.org/GNOME/gimp/-/work_items/7247.

**Other routes:** Termux:X11 or XServer XSDL with proot (https://ivonblog.com/en-us/posts/termux-x11/, https://ivonblog.com/en-us/posts/android-xserver-xsdl/). No native GTK3-on-Android GIMP was found.

**Precedent, not GIMP:** Krita ships officially on Play as `org.krita` (1M+ downloads, updated 2026-09-20).

## 7. Licensing and trademark

**Licences** *(V)*
- GIMP: the application is GPL (v3-or-later per COPYING); libgimp* are LGPL-3.0+. Icons are CC BY-SA 3.0/4.0 and data is intended to be CC0 (https://gitlab.gnome.org/GNOME/gimp/-/blob/GIMP_3_2_6/LICENSE).
- babl: LGPL-3.0+.
- GEGL: the library is LGPL-3.0+, the `gegl` binary and sample apps are GPL-3.0+, poly2tri-c is BSD (https://gitlab.gnome.org/GNOME/gegl/-/blob/GEGL_0_4_72/docs/copyright.adoc). The `common-gpl3+` op module is GPL3+.

**What this means for an APK** *(I)*
- The whole APK is a GPL-3.0 combined work.
- GPLv3 §4–6 apply:
  - ship the licence texts and notices
  - provide the Corresponding Source for every component, including your own Android glue code and patches, alongside the binary or through a 3-year written offer
  - LGPL libraries must remain relinkable, which ordinary `.so` files satisfy
- Watch the bundled dependencies (from general knowledge, not checked against each package's source today): ghostscript is AGPL-3.0, exiv2/gexiv2 are GPL-2.0-or-later, and poppler is GPL-2.0/3.0. All are compatible with GPLv3 distribution.

**GIMP's policy on redistribution** *(V)*
- Selling and redistribution are allowed if the licence and source are provided, and vendors must not "pretend to be the core GIMP team" (https://www.gimp.org/about/selling.html).
- Wilber is CC BY-SA 4.0 by Aryeom Han (https://www.gimp.org/about/linking.html). selling.html still says 3.0.
- Official packages are "vanilla" (snap post).
- The downloads page warns that App Store copies are third-party (https://www.gimp.org/downloads/).

**Trademark** *(V/I)*
- The FAQ says "We have been fighting for the trademark 'GIMP'" to act against fake store apps, and that making the program name configurable is an experiment in progress.
- I found **no active registered GIMP mark held by the project**. The only USPTO hit, serial 79272850, belongs to an unrelated Swiss "Gimp Foundation"; it was filed 2019-04-29 and abandoned 2020-07-21 (https://www.trademarkelite.com/trademark/trademark-detail/79272850/GIMP).
- EUIPO and WIPO were not checked.
- **For contributing upstream:** the 3.2.6 post welcomes contributors "as long as you don't use genAI in your toolset", and the internship rules reject AI-assisted work (https://developer.gimp.org/core/internship/).

## 8. Name check: "Canvas"

*(V unless noted)*
- **CANVAS, US Reg. 3,167,343 (serial 78261662):** Canvas GFX, class 9, "computer software for use in graphic design, desktop publishing…". Registered 2006, **renewed and live**, next renewal due 2026-11-07 (https://www.trademarkelite.com/trademark/trademark-detail/78261662/CANVAS). **This is a direct collision in graphics software.**
  - Related marks: CANVAS X (86372716), CANVAS X DRAW (90794800).
  - Canvas GFX rebranded to Canvas Envision in July 2026 (https://www.canvasenvision.com/press/canvas-gfx-is-now-canvas-envision), and Canvas X Pro is still sold.
- **CANVAS, US Reg. 5,191,435:** Instructure, class 42, incontestable. Instructure actively enforces it (https://domainnamewire.com/2021/11/04/canvas-com/). Its Android apps are `com.instructure.candroid` ("Canvas By Instructure", 10M+ downloads) and `com.instructure.teacher`.
- **Google's own "Chrome Canvas"** is a drawing PWA at canvas.apps.chrome, pinned on Chromebooks (https://support.google.com/chromebook/answer/9200774). **This is the most confusing collision on a Google laptop** *(I)*.
- **Gemini Canvas** is a feature in Google's Gemini app (third-party summaries only).
- **NVIDIA Canvas:** the official page https://www.nvidia.com/en-us/studio/canvas/ now 301-redirects to /studio/. Its status is unclear and it may have been retired *(I)*.
- **Canva:** `com.canva.editor`, 500M+ downloads, and a phonetically close mark.
- **Play Store drawing apps:** a Play search returns drawing apps literally named "Canvas" (`com.rkbapps.canvas`), plus "Drawing Canvas" and "Canvas Draw & Sketch".

Overall, "Canvas" on its own is high-risk for a graphics app.

---

## Recommendation input

- **Target GIMP 3.2.x, starting from 3.2.6**, together with babl 0.1.128 and GEGL 0.4.72.
  - 3.2 is the only maintained stable branch; 3.0 ended at 3.0.8.
  - It has the pen features that matter here: barrel rotation, MyPaint v2, link and vector layers.
  - It carries 28 CVE fixes that matter for an app opening untrusted files.
  - The libgimp API is stable for all of 3.x, so the plug-in ecosystem carries over.
  - Point releases come roughly every 1–5 months; after a quick-fix 3.2.2 two weeks after 3.2.0, gaps were 3 weeks and ~5 months. Bruno Lopes backports fixes to 3.2 while features go to 3.3/3.4 *(V)*.
- **Rebase onto 3.4 later.** No 3.3.x dev release exists yet, and 3.4's new project format and native file choosers are still in progress.
- **Don't wait for GTK4.** Upstream says it is "not yet planning a GTK4 port", so GTK4's Android backend won't help within the 3.x series.
- **Main engineering risks** *(I)*:
  1. There is no GTK3 Android backend. The choices are an embedded Wayland compositor, an embedded X11 server, Broadway, or writing a GDK3 Android backend.
  2. Android's exec rules conflict with 100+ plug-in executables.
  3. There is no SysV shm on bionic.
  4. The cross-build needs a native GIMP (or `exe_wrapper`), build-machine PyGObject/GExiv2, and `-Dcan-crosscompile-gir=true` if you want Python plug-ins.
  5. BinReloc can't find the prefix on Android, so set all `GIMP3_*` variables.
- **Starting point:** Termux's 3.2.0 recipe is the best one available, but it is stale for 3.2.2+ and targets X11.
- **Hard constraints:** GPL source-offer obligations for the whole APK; don't present the app as official GIMP; and pick a different name than "Canvas".
