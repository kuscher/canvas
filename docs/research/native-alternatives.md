<!-- Research agent report, 2026-09-29: native, Qt and Android alternatives to a GIMP port. -->

I'd go with Krita, not GIMP. Installing the official Krita APK gets you most of the "full-featured open-source editor on a Googlebook" result for almost no work. If gaps show up on the HP, a thin patched fork ("KritaBook") costs about as much as OfficeBook did, far less than the GIMP port. What you lose against GIMP is mainly photo-retouching depth and GIMP's plugin and XCF world (details in the verdict). Nothing else in the native, Qt or Android space comes close.

Anything marked *(inference)* is my estimate, not a checked fact.

## 1. Krita on Android and ChromeOS

**Versions**
- Krita 5.3.0 (Qt5) and 6.0.0 (Qt6) came out together on 2026-03-24. The current releases are 5.3.4 and 6.0.4, from 2026-09-15 ([5.3.4 post](https://krita.org/en/posts/2026/krita-5.3.4-released/), [release notes](https://krita.org/en/release-notes/krita-5-3-release-notes/)).
- Both come from one source tree. At tag `v6.0.4`, CMake builds 5.3.4 by default (`BUILD_WITH_QT6 OFF`) and 6.0.4 with Qt6.
- Android only gets the Qt5 build. The release post says "Krita 6.0.4 is not yet functional on Android, so we are not making APK's available."
- The [2026 roadmap](https://krita.org/en/posts/2026/roadmap-2026/) expects 6.0 to become the main version "by the end of the year".

**Official APKs** ([download.kde.org/stable/krita/5.3.4](https://download.kde.org/stable/krita/5.3.4/))

| File | Size |
|---|---|
| krita-arm64-v8a-5.3.4-release-signed.apk | 161 MB |
| krita-x86_64-5.3.4-release-signed.apk | 171 MB |
| krita-armeabi-v7a-5.3.4-release-signed.apk | 150 MB |

So both Googlebook CPU types are covered: x86_64 for the Intel models, arm64 for Snapdragon.

**Play Store (`org.krita`)**
- 1M+ downloads, 3.3★ from 6.45K reviews, updated 2026-09-20, has in-app purchases.
- The listing says: "This is a beta release… not suitable for real work yet… optimized for big screen devices (tablets and chromebooks) we are not making it available for phones."
- The manifest sets `normalScreens=false` and `smallScreens=false`.
- Krita's own position is split: "We consider Krita on ChromeOS as ready for production. Krita on Android is still beta" ([5.2.2 post](https://krita.org/en/posts/2023/krita-5-2-2-released/)), while 5.3.x posts still say "beta… chromebooks and tablets only".
- F-Droid still has 5.1.5 from 2023, so it's stale.

**Laptop readiness, from the master manifest** ([AndroidManifest.xml](https://invent.kde.org/graphics/krita/-/blob/master/packaging/android/apk/AndroidManifest.xml))
- `resizeableActivity="true"`.
- `configChanges` includes `screenSize|smallestScreenSize|density|keyboard|navigation`, so a freeform resize shouldn't restart the app. That is the same thing your OfficeBook patch 0002 had to add.
- It declares `<uses-feature android.hardware.type.pc required=false>`.
- It requires GLES 3.0 and targets SDK 36 (minimum 24), built with NDK 27.3.
- *(inference)* It should install and resize on a Googlebook with nothing changed. Check that on the HP and the ASUS.

**Open Android and ChromeOS bugs that matter on a laptop** (52 open on [bugs.kde.org](https://bugs.kde.org/buglist.cgi?product=krita&platform=Android))

| Bug | Problem |
|---|---|
| [524349](https://bugs.kde.org/show_bug.cgi?id=524349) | Popups have no close buttons on ChromeOS |
| [524252](https://bugs.kde.org/show_bug.cgi?id=524252) | Keyboard typing stops after a menu or popup appears |
| [523346](https://bugs.kde.org/show_bug.cgi?id=523346) | Brush editor is oversized and can't be resized at HiDPI |
| [506040](https://bugs.kde.org/show_bug.cgi?id=506040) | "Choose File Type" dialog is clunky |
| [524268](https://bugs.kde.org/show_bug.cgi?id=524268) | G'MIC actions show up but aren't supported on Android |
| [522060](https://bugs.kde.org/show_bug.cgi?id=522060) | Crash on import on Android 16 |
| [430829](https://bugs.kde.org/show_bug.cgi?id=430829) | Pen lag on ChromeOS |

- Forum reports of keyboard shortcuts stopping on a Chromebook: [krita-artists](https://krita-artists.org/t/on-android-chromebook-plain-keyboard-shortcuts-stop-working-after-clicking-on-textbox/123846).
- File access: the roadmap says "working with the file sandboxing is seriously complicated", and there are save-permission threads ([1](https://krita-artists.org/t/cannot-save-file-on-android-device/170413), [2](https://krita-artists.org/t/how-can-i-save-krita-images-on-chromebook/169970)).
- The August 2026 report added an Android interface-scaling menu ([report](https://krita.org/en/posts/2026/monthly-report-2608/)).
- A QML mobile UI prototype is in progress. The developers promise "to keep the desktop UI available one way or another" ([thread](https://krita-artists.org/t/about-android-mobile-ui-on-dex/186712)).

**Missing on Android specifically**
- No Python plugins: there's no interpreter bundled ([forum thread](https://krita-artists.org/t/ability-to-add-python-plugins-on-android-please/65985); [493806](https://bugs.kde.org/show_bug.cgi?id=493806) asks for it now that CPython supports Android).
- No G'MIC: `build-tools/ci-scripts/build-plugins.py` prints "Skip building GMic plugin for Android". The qmic bridge plugin itself is compiled.

**How the Android build is made (for a KritaBook fork)**
- The CI recipe is `build-tools/ci-scripts/android.yml`. It runs on KDE's VM image `krita-android-sdk36-2204` and clones [krita-deps-management](https://invent.kde.org/packaging/krita-deps-management) and krita-ci-utilities.
- It runs `run-ci-build.py --platform Android/<ABI>/Qt5/Shared`, then `build-android-package.py` (androiddeployqt plus Gradle).
- Qt is a Krita fork, `invent.kde.org/szaman/qt5` (5.15 plus Android patches: 16K pages, NDK 27).
- **The prebuilt dependencies are public.** Registries like [teams/ci-artifacts/krita-android-x86_64](https://invent.kde.org/teams/ci-artifacts/krita-android-x86_64) serve them without login. For example, the `ext_qt` archive is 169 MB, updated 2026-09-25, and downloads with HTTP 200 unauthenticated. So a fork only has to compile Krita itself, not about 90 dependencies.
- KDE's nightly Android jobs take 12–28 minutes per ABI with a warm ccache (pipelines from 2026-09-17 to 09-25). *(inference)* On a cold 4-core GitHub runner, expect roughly 1.5–3 hours per ABI.
- The Java layer is small: 13 files in `packaging/android/apk/src/org/krita/android` (MainActivity, DocumentSaverService, ScalingDialog, VideoEncoder, Donation*). That's patchable the same way as OfficeBook. The app also bundles Play Billing 9.0, which a fork would stub out.
- The [trademark policy](https://krita.org/en/posts/2013/krita-trademark-policy/) means a redistributed fork must change the name and icon.

**Photo-editing depth compared with GIMP**
- Selections: rectangle, ellipse, polygon, freehand, contiguous, similar-colour, magnetic, path, and selection masks ([tools](https://docs.krita.org/en/reference_manual/tools.html)).
- Layers: paint, vector, group, clone, file, fill and filter layers, plus transparency, filter, transform and colorize masks. Non-destructive editing is mature ([filters](https://docs.krita.org/en/reference_manual/filters.html)).
- Transforms: free, perspective, warp, cage, liquify and mesh.
- Colour: LCMS2 and OCIO, CMYK, 16-bit and 32-bit float, HDR.
- Text: rewritten in 5.3/6.0 with on-canvas editing.
- PSD: read and write, including layer styles.
- XCF import was **removed in 5.3.4** because it relied on an unmaintained, insecure library. GIMP 3 XCF files never loaded anyway.
- Weaker than GIMP for retouching:
  - Healing is only "Smart Patch".
  - No GEGL operations, no Foreground Select.
  - No Script-Fu or Python-Fu, and no batch tools like BIMP.
  - On Android, no G'MIC or Python either.

**Licence and AI policy**
- GPL-3.0.
- The repo [README](https://invent.kde.org/graphics/krita/-/blob/master/README.md) has had an **"AI Moratorium" since 2026-04-21**: "Until we make a decision, the use of AI when working on Krita is not allowed." The decision is due in October 2026. The maintainer has said "I don't want us to accept any LLM-generated code into Krita" ([forum](https://krita-artists.org/t/policy-on-llm-code/178248)).
- **So a KritaBook fork is downstream-only for now.**
- GIMP is no different. Its [3.2.6 notes](https://www.gimp.org/news/2026/09/10/gimp-3-2-6-released/) say "as long as you don't use genAI… everyone is very welcome".
- Prior art: [serika12345/librepaint](https://github.com/serika12345/librepaint) is an AI-assisted Krita fork (GPL-3.0, 9★, created 2026-08) that works on iPad. Its Android side hasn't gone past "prepare the dependency prefix".

## 2. Pinta

- Version 3.1.2 (2026-04-02), MIT, 4.1k★, C# on .NET 10. Uses GTK ≥ 4.18 and libadwaita ≥ 1.7 through GirCore ([release notes](https://github.com/PintaProject/Pinta/releases/tag/3.1)).
- Features: layers with blend modes, adjustments and effects, magic wand, lasso (including polygon), clone, text, unlimited undo. No masks, no colour management, no PSD or XCF *(inference from the feature list; ORA is its layered format)*.
- **GTK's Android backend** is real and active. Recent commits in [gdk/android](https://gitlab.gnome.org/GNOME/gtk/-/tree/main/gdk/android) are from 2026-09, with GTK 4.24.0 released 2026-09-11. It has SAF-backed GFile, GL context files and a `ToplevelActivity` per window, so dialogs would open as separate freeform windows *(inference)*.
- libadwaita's demo now runs on Android ([libadwaita 1.10 post](https://nyaa.place/blog/libadwaita-1-10/)).
- The builder is [pixiewood](https://github.com/sp1ritCS/gtk-android-builder), and it supports only meson apps with a C `main`. Planify ([PR #2635](https://github.com/alainm23/planify/pull/2635)) and Tuba have experimental Android builds.
- **Pinta has no Android attempt, and GirCore has zero Android issues.** Blockers:
  - Getting a .NET runtime on Android to host GTK's Java glue. NativeAOT for linux-bionic is experimental.
  - Mono.Addins (reflection and Cecil) doesn't work under NativeAOT.
  - Tmds.DBus.
- Effort L–XL, for an editor that's shallower than Krita.

## 3. Qt editors and GTK photo apps

| App | Status | Android feasibility |
|---|---|---|
| [Photoflare](https://github.com/PhotoFlare/photoflare) 1.7.4 (GPL-3.0, 471★) | Qt6 Widgets, GraphicsMagick++, OpenMP; G'MIC-Qt optional | Size M through Qt for Android, like MixxxBook. **No layers.** The layered version, "PhotoFlare Studio", is a commercial €79 beta. Low value. |
| KolourPaint (KF6) | Needs KIO and KXmlGui; KDE CI builds Linux, Windows and macOS only ([.kde-ci](https://invent.kde.org/graphics/kolourpaint)) | M–L, and it's MS-Paint-level with no layers. |
| LazPaint (GPL-3.0, 551★, last push 2026-03) | Lazarus LCL; no maintained Android LCL widget set | XL (UI rewrite). |
| Pixelitor | Java Swing; no AWT on Android | No. |
| PhotoQt | Image viewer, not an editor | n/a |
| Inkscape | GTK4 port lives in unreleased 1.5; stable is 1.4.4 | Later, possibly on GTK Android; XL. |
| darktable | GTK3; a GTK4 migration was assessed as "large, high-risk, multi-month" ([#20433](https://github.com/darktable-org/darktable/issues/20433)) | No. |
| RawTherapee | GTK3; GTK4 [issue](https://github.com/Beep6581/RawTherapee/issues/6542) open, no port | No. |

The practical raw developer is [RapidRAW](https://github.com/CyberTimon/RapidRAW) 1.6.4 (AGPL-3.0, 10.2k★). It ships an official **Android APK, arm64 only**, so it runs on the Snapdragon Googlebooks but not the Intel ones.

## 4. Existing native open-source Android editors

| App | Licence / ★ / activity | What it is |
|---|---|---|
| [ImageToolbox](https://github.com/T8RIN/ImageToolbox) 4.2.0 (2026-09-04) | Apache-2.0, 14.8k★, daily commits; Play and F-Droid | Kotlin/Compose. 500+ filter chains, batch processing, crop, resize, EXIF, AI background removal (ONNX), OCR, JXL, APNG, WebP, SVG tracing, collage, PDF tools. Only "Markup Layers" for stickers and text, so **not a layer compositor**. |
| [Drawpile](https://github.com/drawpile/Drawpile) 2.3.0 | GPL-3.0, 1.3k★; Qt6 on Android | Collaborative painting app with layers. APKs are arm64 and v7 only. It shows Qt6 plus pen input works on Android. |
| [Pixelorama](https://github.com/Orama-Interactive/Pixelorama) 1.2.3 | MIT, 10.4k★ | Godot pixel-art editor; official `.apk`. |
| [LibreSprite](https://github.com/LibreSprite/LibreSprite) | GPL-2.0, 8.5k★ | Sprite editor; Android zip only in dev builds. |
| [Paintroid / Pocket Paint](https://github.com/Catrobat/Paintroid) 2.14.1 | AGPL-3.0, 501★ | Simple layered paint app for kids and education. |
| [Fossify Paint](https://github.com/FossifyOrg/Paint) | GPL-3.0, 315★ | Doodle canvas. |
| [PhotoEditor](https://github.com/burhanrashid52/PhotoEditor), [imagine](https://github.com/whyisitworking/imagine) | MIT libraries | Building blocks only. |

No open-source Android app is a layered photo editor on the level of GIMP or Krita. Krita is the only one.

## 5. Proprietary context (not for porting)

The Snapseed and Photoshop lines are sourced; the other lines are general knowledge I didn't re-check.

- **Snapseed 4.x** (free, Google): redesigned in [May 2026](https://9to5google.com/2026/05/08/snapseed-4-android-update/) with non-destructive edits, smart masking and batch; [4.1 adds RAW](https://9to5google.com/2026/07/13/snapseed-4-1-raw/). Photo adjustments only, no layers.
- **Photoshop (Android)**: beta since June 2025 with layers, masks, PSD and Firefly. Free during beta, then about $8 a month. Adobe doesn't list tablets or Chromebooks as supported ([TechCrunch](https://techcrunch.com/2025/06/03/adobe-launches-beta-version-of-its-photoshop-app-on-android)).
- **Photoshop Express**: quick fixes and templates, freemium.
- **Lightroom mobile**: RAW development, masking, AI remove; subscription.
- **Ibis Paint X**: layered illustration and comics; ads or subscription.
- **Infinite Painter**: pro painting with layers; one-time pro unlock.
- **Photopea**: web PSD editor with ads; runs as a PWA and is the closest match to GIMP or Photoshop features.

## 6. GIMP on GTK4 or native Android

- **No GTK4 fork and no native Android port exist.** GNOME GitLab has no `gtk4` or `android` branches.
- 3.2.6 only starts "early preparations toward an eventual GTK4 port" (gtk_widget_set_visible, GdkEvent getters and so on) ([Phoronix](https://www.phoronix.com/news/GIMP-3.2.6-Released)).
- New platforms in 3.2.6 are Windows ARM, Linux ARM AppImage, Flatpak and Snap, and a native macOS build. There are no announced mobile or Android plans.
- **Termux already packages GIMP 3.2.0** in [x11-packages/gimp](https://github.com/termux/termux-packages/tree/master/x11-packages/gimp), built against Bionic with gtk3, gegl, babl, libmypaint and pygobject (updated 2026-07-21). If you go ahead with the GIMP port, reuse those recipes. It's essentially your planned dependency tree.

## Comparison

| | GIMP native port | Krita, official APK | KritaBook (patched) | Pinta via GTK4-Android | Photoflare (Qt) | ImageToolbox |
|---|---|---|---|---|---|---|
| **Effort** | XL (about 150 libraries plus a termux-x11 server) | **S** (install and test) | **M** (fork + GitHub Actions using KDE's public prebuilt dependencies; small Java/Qt patches; rename) | L–XL (.NET on Bionic, GirCore, Mono.Addins, experimental GTK backend) | M | S (install) / M (desktop layout PRs) |
| **Photo depth vs GIMP** | 100% | ~75% *(inference)*: strong layers, masks, non-destructive filters, colour management, transforms; weak healing; no GEGL, G'MIC or scripting on Android | ~80–85% if G'MIC and CPython get enabled *(inference)* | ~35% | ~20% (no layers) | ~40% for one-shot, batch and AI operations; no compositing |
| **Painting / pen** | Good (MyPaint brushes, pressure) | **Best in class**; S Pen and USI support; ChromeOS pen-lag bug open | Same | Basic | Basic | Basic doodling |
| **PSD / XCF** | XCF native; PSD with layers | PSD read and write (with styles); **XCF removed in 5.3.4**; ORA | Same (XCF import could be revived via GIMP-exported ORA) | ORA only *(inference)* | Flat formats only | Flat formats only |
| **Native feel on a laptop** | Desktop UI inside an X11 surface; clipboard, file and IME bridging all DIY *(inference)* | Desktop Qt UI in one resizable window; declares type.pc; known popup, focus and HiDPI bugs; clunky SAF dialogs | Fix those plus native SAF, recent files, desktop defaults | libadwaita look; one Activity per window | Qt Widgets in one window | Truly native Compose M3, but phone-first |
| **Maintenance** | Very high (rebuild the dependency stack every release) | None (upstream releases monthly) | Medium: rebase on each tag; **Qt5→Qt6 switch on Android is coming**; upstream won't take AI-written patches (moratorium) | High (tracking an experimental backend) | Low–medium | None / low |
| **Licence** | GPL-3.0+ | GPL-3.0 | GPL-3.0; must rename per trademark | MIT | GPL-3.0 | Apache-2.0 |

## Verdict

**Krita gives the "similar outcome" for much less work.**
1. Install the official 5.3.4 APK on the HP (arm64) and the ASUS (x86_64) now. Check freeform resize, keyboard shortcuts, pen pressure, and saving through SAF.
2. If the laptop gaps bite, build a **KritaBook** fork: tag `v6.0.4` built as Qt5 (5.3.4), dependencies pulled from KDE's public package registries, GitHub Actions as with OfficeBook.
   - Patch popups and focus ([524349](https://bugs.kde.org/show_bug.cgi?id=524349), [524252](https://bugs.kde.org/show_bug.cgi?id=524252)), HiDPI dialogs, and SAF open/save with persisted permissions.
   - Then consider enabling G'MIC (the biggest photo-filter gain) and later CPython for Python plugins.
   - Rename and rebrand it, and drop Play Billing.
   - Keep it downstream until Krita settles its AI policy, due this October.

**What you lose compared with GIMP:**
- XCF, especially GIMP 3's format.
- GIMP's retouching tools: heal, Foreground Select, GEGL and non-destructive GEGL filters.
- Script-Fu, Python-Fu, BIMP and the plugin ecosystem.
- G'MIC on Android (until you enable it).
- The familiar GIMP UI for existing GIMP users.

For photo work, pair Krita with Snapseed or RapidRAW (arm64 only) for raw development, and ImageToolbox for batch conversions.

Keep the GIMP port only if exact GIMP parity (XCF, plugins, GEGL) is the actual goal. In that case, start from Termux's recipes rather than scratch.

Pinta, Photoflare and the other Qt editors are poor value compared with Krita.
