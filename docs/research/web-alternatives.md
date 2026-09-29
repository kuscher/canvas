<!-- Research agent report, 2026-09-29: web-based editors (Graphite and others) as alternatives to a GIMP port. -->

# Web-based image editors as an alternative to a native GIMP port on Googlebooks

## TL;DR
- **Graphite won't replace GIMP before 2027 or 2028.** It is a very good static WASM app and would be easy to wrap in a WebView. But its own manual calls the raster brush a "prototype". It has no selection tools yet, and clone/heal is scheduled for 2028.
- **miniPaint and Kleki would each take about a day to wrap.** They are Paint.NET-lite and a painting app, not GIMP.
- **Patchy is the find that matters** (github.com/SethRobinson/Patchy). It's an MIT-licensed C++/Qt 6 editor in the Photoshop style that runs in the browser as WASM. Version 1.00 came out 2026-09-27. It has a deep photo toolset and only about 6 dependencies, against GIMP's roughly 150. It could ship as a WebView wrapper or as a Qt-for-Android build, like MixxxBook. The risk is that it's 4 months old, has one maintainer and was written with a lot of AI help.
- **Krita already ships official Android APKs for x86_64 and arm64**, labelled beta. That's the zero-port option for a native, full-featured open-source editor.
- **No GIMP or GEGL WebAssembly project of any substance exists.**

---

## 1. Graphite (graphite.art; the old graphite.rs domain redirects)

### Status as of 2026-09-29
- **No versioned releases.** The web app is deployed continuously at editor.graphite.art. The only GitHub release is a stale "latest-stable (Alpha 4 series)" tag from 2022 (GitHub API).
- **Repo:** 27,382 stars, last push 2026-09-29. The license is now **MIT OR Apache-2.0** (README; the GitHub API still reports Apache-2.0).
- **Roadmap** (https://graphite.art/features/#roadmap; status comes from each item's CSS class):
  - Alpha 4 (2025) is complete: "Performance, animation, desktop RC1".
  - Alpha 5 (2026) is in progress:
    - Done: "Offline caching and installable PWA", "Dockable panels", "Embedded resource management".
    - Ongoing: "Stable document format", "New and improved brush tool", "Stylus drawing with pressure and tilt", "Desktop app RC2-6".
    - Later in Alpha 5: "GPU-accelerated raster rendering", "Basic PDF export", "Desktop app release and 1.0 launch".
  - **Beta 1 is tagged 2027:** "Selection tools and marquee masking", "Raster adjustments, filters, and effects", "Liquify", "CMYK, spot color, and ICC profiles", "Saving over local files (web version)".
  - **Beta 2 is tagged 2028:** "History brush and clone stamp tools", "Raw photo processing".
  - **LTS / future:** "Import: PDF, EPS, AI, DXF, PSD, TIFF", "Neural nodes/tools like Magic Wand", "Tablet app and keyboard-free controls".
- **Desktop app:** release candidates are handed out only through Discord (https://graphite.art/). RC1 came out at Christmas 2025 and RC2 on 2026-01-06 as a Flatpak. It "is not yet something I would deploy for critical client work" (https://itsfoss.com/graphite-graphics-editor/, Feb 2026).

### Raster and photo editing today
- **Manual** (https://graphite.art/learn/introduction/features-and-limitations/): "A prototype Brush tool exists… very limited in its capabilities and there are multiple bugs and performance issues… The tool will be fully rewritten." Raster work is limited to importing images and applying "nondestructive global effects like color adjustment filters".
- **Brush:** diameter, hardness and flow. `brush_tool.rs` already records `input.mouse.pressure` samples, but the roadmap item for pressure and tilt is still "ongoing". It "lagged noticeably" on small strokes (It's FOSS), and 1–2 px sizes "can cause missed strokes" (https://lwn.net/Articles/1051242/, Dec 2025).
- **Selections (marquee, lasso, magic wand):** none. Marquee is Beta 1; magic wand is LTS.
- **Masks:** only a "Clipping Mask" node. Raster masking is Beta 1.
- **Adjustments and filters:** these exist as nodes in `node-graph/nodes/raster/src/adjustments.rs`:
  - Adjustments: Levels, Curves, Brightness/Contrast, Hue/Saturation, Vibrance, Exposure, Channel Mixer, Selective Color, Color Balance, Photo Filter, Posterize, Threshold, Gradient Map, Black & White, Invert, LUT.
  - Filters: Gaussian/box blur, median, dehaze.
- **Clone/heal:** Beta 2 (2028). There's no dedicated crop tool; you use artboards (inference). A text tool exists.
- **Formats:** imports SVG, PNG, JPEG and WebP; exports PNG, JPEG and SVG (LWN). The native format is `.graphite` (JSON). No PSD or XCF.
- **Color management:** Beta 1. Large images: "Scalable raster compositing is still experimental."
- **Offline catch:** the font list comes from `https://api.graphite.art/font-list` and the fonts from `fonts.googleapis.com` (`editor/src/messages/portfolio/fonts/utility_types.rs`). A no-INTERNET APK would need that patched.

### Tech
- **Stack:** Rust compiled to WASM with wasm-bindgen, a Svelte 5 and Vite frontend, rendering through Vello on wgpu/WebGPU.
- **WebGPU is not required.** Without it the editor falls back to "SVG Preview": "*Normal*, *Outline*, and *Pixel Preview* render modes are not available in this browser… requires WebGPU support" (`document_message_handler.rs`).
- **No SharedArrayBuffer or COOP/COEP needed** (inference). The WASM rustflags have `+bulk-memory` and `--max-memory=4GB` but no `+atomics`, and the frontend has no COOP/COEP headers.
- **Static build:** `cargo run build web` runs `vite build` (`tools/cargo-run/src/main.rs`). The WASM is split into parts to stay under Cloudflare Pages' 25 MB file limit.
- **WebView quirk:** `NumberInput.svelte` uses `requestPointerLock`, which WebView doesn't implement (see §2), so drag-to-scrub on number fields will probably degrade.
- **Desktop shell:** winit + wgpu + Vello + **CEF**, with the UI rendered off-screen into wgpu textures (`desktop/ui/Cargo.toml`). Platform directories exist for linux, mac and win only. Tauri was abandoned "due to an insurmountable technical incompatibility" (LWN). There's no Android build, and CEF has no Android port, so wrapping the web build in a WebView is the only route.

### Verdict
Graphite is an excellent vector and procedural tool and very easy to package. It is not realistic as a GIMP replacement for photo editing or painting in 2026. Both reviews agree on that.

---

## 2. Android System WebView facts (around v153)

| Capability | Status | Source |
|---|---|---|
| **WebGPU** | **Probably on; check on the device.** Chromium enables `kWebGPUService` by default on Android. The adapter allowlist admits "ARM, Qualcomm, and Intel GPUs… on Android 12+ on Vulkan", so both Intel and Snapdragon Googlebooks qualify. The WebView flag list includes WebGPU flags. A blink-dev WebGPU intent (Jan 2026, M146) says features are "immediately available on Android, **Android WebView**…". Third-party sites (caniwebview, utsubo) still say WebView support is unconfirmed. It's blocked under Android Advanced Protection. | `gpu/config/gpu_finch_features.cc`, `gpu/config/webgpu_blocklist_impl.cc`, `ProductionSupportedFlagList.java` (chromium/chromium), https://groups.google.com/a/chromium.org/g/blink-dev/c/uSCwz1uZpto/m/ApW7HBB1CQAJ |
| WebGL2, OffscreenCanvas | Yes (OffscreenCanvas since WebView 69) | MDN browser-compat-data (BCD) |
| **SharedArrayBuffer** | **Off by default**: `crossOriginIsolated` is false because WebView has no renderer process isolation (crbug 40914606). **New:** androidx.webkit **1.18.0-alpha01 (2026-09-09)** adds `Profile#setCrossOriginIsolatedAllowlist`. It needs `WebViewFeature.CROSS_ORIGIN_ISOLATED_ALLOWLIST`, and the page must send a `Document-Isolation-Policy` header, which WebViewAssetLoader can add to its response (inference). | https://developer.android.com/jetpack/androidx/releases/webkit; `androidx/webkit/Profile.java` |
| File System Access (`showOpenFilePicker` / `showSaveFilePicker`) | Shipped in M132 for Android **and WebView**. The app must implement `WebChromeClient#onShowFileChooser`. Known limits: MIME filters are ignored, save-as can't create new files, no atomic writes. | https://groups.google.com/a/chromium.org/g/blink-dev/c/x3IcFv2jY6c |
| Pen input | `pressure` and `tiltX` (55), `altitudeAngle` (86), `getCoalescedEvents` (58) and `getPredictedEvents` (77) are all listed for WebView. That BCD data is mirrored from Chrome rather than tested on WebView. **Pointer Lock is not implemented in WebView.** | MDN BCD; `android_webview/browser/aw_permission_manager.cc` |
| Clipboard | Writing text and sanitized images is auto-granted. `navigator.clipboard.read()` is **denied** because CLIPBOARD_READ_WRITE isn't implemented (crbug 1271620). Ctrl+V `paste` events should still work (inference). Local Font Access is also denied. | `aw_permission_manager.cc` |

WebView also force-disables SharedWorkers and the legacy FileSystem API (`aw_main_delegate.cc`).

---

## 3. Other open-source web editors (stars and last push from the GitHub API, 2026-09-29)

**Patchy** — github.com/SethRobinson/Patchy. MIT, 395★, created 2026-05-29, pushed today, **v1.00 on 2026-09-27**.
- **What it is:** a Photoshop-style editor in C++/Qt 6. The "browser version is the same editor compiled to WebAssembly" (patchyimageeditor.com).
- **Photo tools:** Healing Brush, Spot Healing, Patch, Clone Stamp, dodge, burn and smudge. Selections plus Magic Wand and Quick Mask. Non-destructive adjustment layers (Levels, Curves, Hue/Saturation and others). Smart Objects and Smart Filters, a 32-effect Filter Gallery, Liquify, text, vector shapes, Camera Raw via LibRaw, and "Pen/stylus pressure and size dynamics".
- **PSD:** layered PSD/PSB read and write. Its own benchmark gives a 98.83% render match against Photoshop 2026, vs Photopea at 97.13% and GIMP 3.2.4 at 88.11%. The benchmark is self-published and corpus-specific.
- **Dependencies:** only Qt 6, miniz, stb_image, zstd, Little CMS, LibRaw and libheif (`NOTICE-THIRD-PARTY.md`).
- **Web build** (`docs/wasm.md`): Emscripten 4.0.7 with Qt 6.10.3 `wasm_multithread`, so it **needs COOP/COEP, i.e. SharedArrayBuffer**. There is a single-thread build, but "current ST builds also die under workload". Other limits: a 4 GB memory ceiling, saves delivered as browser downloads, about 23 MB of bundled web fonts, and CPU-only rendering.
- **Gaps:** no 16/32-bit editing, no CMYK editing, no GPU acceleration. The README says the project "was developed with significant assistance from AI tools". Single maintainer. No Android build exists.

**miniPaint** — viliusle/miniPaint. MIT, 3,463★. v4.14.3 on 2026-04-20 was just an npm update and a fonts-key change; the previous real work was in 2024, so it's effectively in maintenance.
- **Features:** layers (no masks), rectangle selection plus magic wand, clone, crop, resize, color corrections and about 40 effects. The brush reads `e.pressure` (`src/js/tools/brush.js`).
- **Formats:** PNG, JPG, BMP, WebP, GIF, TIFF, plus JSON for layered saves. **No PSD.** Plain JS.
- **Offline:** the text tool depends on a Google Fonts key (inference: you'd bundle the fonts).

**Klecks / Kleki** — bitbof/klecks. MIT, 381★ (kleki.com is much bigger), pushed 2026-09-27.
- **Features:** a painting app with layers, "Pen-support with pressure and stabilizer", 7 brush types, selection, fill, text, shapes and gradient. WebGL filters (blur, tilt-shift, curves, distort, noise), transform, warp, perspective.
- **PSD import/export** via `ag-psd`. TypeScript with Parcel and a service worker, so it works offline and builds to static files (`npm run build`).
- **Photo work is weak:** no heal or clone, and no layer masks (masks are inference).

**BitMappery** — igorski/bitmappery. MIT, 344★, pushed 2026-09-01. A non-destructive Vue/TS editor with layers, **masks**, custom brushes, text, filters, PSD import (psd.js) and Dropbox. The author says it's "the bare minimum of what I require". One developer.

**Pixelorama** — MIT, 10.4k★. Godot-based pixel art editor with a web build **and an official `Pixelorama.apk`** (v1.2.3, 2026-09-15). Pixel art only.

**Drawpile** — GPL-3.0, 1.3k★. Collaborative Qt painting app with Android APKs and a browser client (https://docs.drawpile.net/help/tech/browser.html). Painting only.

**Also checked but ruled out:**
- JSPaint (MIT, 7.9k★) is an MS Paint clone. Piskel (Apache-2.0, 12.8k★) is for sprites.
- **TUI Image Editor is archived** (2023). Filerobot (MIT, 1.9k★) is an embeddable React crop/filter/annotate widget with no painting layers.
- **Photopea is proprietary.** Its GitHub repo is only an issue tracker, so it can't be bundled.
- Openshop (16★) and opengpex (GPL-3.0, 4★) are too immature.
- RapidRAW (AGPL, 10k★) is a Lightroom-style RAW developer on Tauri, not a GIMP analogue.
- **GIMP/GEGL to WASM:** nothing of substance. The only hit is `discere-os/gegl.wasm` (0★, two days of commits in Sept 2025). GTK3 Broadway can show GIMP in a browser but needs a Linux host. You could run it in the Terminal VM with a WebView front end, the way VSCodeBook works, but expect no pen pressure, latency, and the VM stopping whenever Terminal closes (inference).
- **Krita** (GPL-3.0, native, not web): official APKs for 64-bit Intel, 64-bit Arm and 32-bit Arm; current version is 5.3.4. "Krita on Android is still beta; and is meant to run on chromebooks and tablets only" (https://krita.org/en/posts/2026/krita-5.3.1.1-released/). It has full layers, masks, selections including magic wand, filters, clone, PSD and color management, with a desktop Qt UI.

---

## 4. Comparison

| | Effort to ship as a Googlebook APK | Photo-editing depth | Painting / pen | PSD / XCF | Maturity | License | Native-feel potential |
|---|---|---|---|---|---|---|---|
| **GIMP 3.2.6 native port** | **XL** (~150 libs plus an X server) | ★★★★★ (GEGL, high bit depth, ICC, G'MIC and plug-ins) | ★★★ (pressure via X/Wayland input plumbing, which you'd have to build) | PSD r/w (88% match in Patchy's test), **XCF native** | Very high | GPL-3.0 / LGPL | Medium (GTK in an embedded X server) |
| **Graphite (WebView)** | **S** (static build, no SharedArrayBuffer, SVG fallback without WebGPU; patch out online fonts) | ★ (adjustment nodes only; no selections, masks or heal until 2027–28) | ★ (prototype brush; pressure ongoing) | No / No (PSD import is "LTS") | Alpha | MIT OR Apache-2.0 | High for UI polish, but pointer lock is missing |
| **miniPaint (WebView)** | **S** | ★★ (wand, clone, effects; no masks) | ★★ (pressure brush) | No / No | Stable but stagnant | MIT | Medium |
| **Kleki / Klecks (WebView)** | **S** | ★½ | ★★★★ (pressure, stabilizer) | PSD r/w / No | Stable, active | MIT | Medium–high |
| **Patchy — WASM in WebView** | **M** (alpha androidx.webkit allowlist + Document-Isolation-Policy for SharedArrayBuffer, a download/file bridge, 4 GB cap, large payload) | ★★★★ (heal, clone, patch, masks, adjustment layers, smart filters, RAW) | ★★★ (pressure dynamics; tablet events are handled in Qt-WASM per `wasm-input.md`) | **PSD/PSB r/w (best non-Adobe in its test)** / No | New: 1.00 was 2 days ago, bus factor 1 | MIT | Medium (Qt canvas inside a WebView) |
| **Patchy — Qt for Android native** (inference) | **M** (Qt 6.10 Android + 6 small deps, same CI pattern as MixxxBook; gate the Windows-only 8bf/scanner code) | ★★★★ | ★★★ (Qt tablet events on Android, inference) | same | same | MIT | High (desktop Qt Widgets, native SAF dialogs) |
| **Krita Android (official APK)** | **XS–S** (sideload and test; a rebrand or rebuild would be L) | ★★★ | ★★★★★ | PSD r/w, ORA/KRA / No | Mature app; Android port still beta | GPL-3.0 | High (native Qt) |

---

## 5. Verdict

- **None of the pure JS editors gets close to GIMP.** Graphite is the most promising project long term, but its raster toolset (selections, masks, clone/heal) is on the 2027–2028 roadmap.
  - It costs almost nothing to package: static files, no SharedArrayBuffer, and it degrades gracefully without WebGPU. A "Graphite Book" makes sense as a vector/procedural app or a placeholder, not as a GIMP substitute.
  - miniPaint and Kleki are one-day wins for light edits and sketching.
- **Patchy is the only web-capable open-source editor with GIMP- or Photoshop-class photo tools**: heal, clone, patch, selections, masks, adjustment layers, filters and PSD round-trips. Its dependency list is tiny next to GIMP's.
  - Suggested order: spend an hour on patchyimageeditor.com in Chrome on the HP Googlebook. Then choose between a WebView wrapper (needs the new alpha cross-origin-isolation allowlist) and a Qt-for-Android build. The Qt build is probably better on feel and file access, and reuses the MixxxBook toolchain.
  - Either way it's roughly an **M** job against GIMP's **XL**.
  - What you'd lose compared with GIMP: XCF, 16/32-bit float editing, full ICC/CMYK workflows, GEGL and G'MIC filter breadth, and Script-Fu/Python plug-ins. You also take on project risk: 4 months old, a single developer, largely AI-written code.
- **Try Krita's official x86_64/arm64 APK on the Googlebook first.** It's zero porting and already a GIMP-class open-source editor, strongest at painting. If it behaves in freeform windows with mouse and keyboard, it covers much of the "similar outcome".
- **The native GIMP port is still the only way to get GIMP itself**: XCF, the plug-in ecosystem, and a high-bit-depth GEGL pipeline.

**Things to check on the device** (DevTools on a debuggable WebView build):
- `await navigator.gpu?.requestAdapter()`
- `WebViewFeature.isFeatureSupported(CROSS_ORIGIN_ISOLATED_ALLOWLIST)` on WebView 153
- `PointerEvent.pressure` from the pen on the Googlebook
