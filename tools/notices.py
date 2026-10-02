#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Writes THIRD_PARTY_NOTICES.md: every component inside the Canvas APK, with
its licence and the full licence texts.

    tools/notices.py [--patchy DIR] [--qt DIR] [--offline]

- Patchy itself, and the sections of Patchy's NOTICE-THIRD-PARTY.md that cover
  what the Android build ships (fonts, vendored libraries, pattern and palette
  presets). Windows-, web- and test-only sections are left out.
- Qt: the modules and plugins the APK carries, and every third-party component
  Qt's own SBOMs (sbom/*.spdx.json in the Qt for Android kit) declare for them.
- The Android side: AndroidX Core (with its AndroidX and Kotlin dependencies)
  and LLVM's libc++.
- The full text of every licence named, from licenses/texts/<SPDX id>.txt
  (fetched once from spdx/license-list-data and committed), plus the
  LicenseRef texts Qt's SBOMs carry.

The app shows the result under Help > About Patchy > Licenses.
"""
import argparse
import json
import pathlib
import re
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE = pathlib.Path.home() / ".cache" / "canvas"
TEXTS = ROOT / "licenses" / "texts"
SPDX_TEXT = "https://raw.githubusercontent.com/spdx/license-list-data/main/text/{}.txt"

# Patchy NOTICE sections the Android build ships, and how to title them here.
PATCHY_SECTIONS = {
    "Noto Naskh Arabic": "Noto Naskh Arabic",
    "Bundled fonts (web build only)": "Bundled fonts",
    "miniz": "miniz",
    "stb_image": "stb_image",
    "Zstandard": "Zstandard",
    "Little CMS": "Little CMS",
    "LibRaw": "LibRaw",
    "Bundled photo-texture pattern presets (Poly Haven, CC0)": "Photo-texture pattern presets (Poly Haven, CC0)",
    "Built-in color palette presets": "Built-in color palette presets",
}
PATCHY_SECTION_LICENSES = ["OFL-1.1", "MIT", "BSD-3-Clause", "CDDL-1.0", "CC0-1.0"]

# Qt modules and plugins in the APK (androiddeployqt's selection for Patchy).
QT_SBOMS = ["qtbase", "qtsvg", "qtimageformats", "qtdeclarative"]
QT_MODULES = ["Core", "Gui", "Widgets", "Svg", "Network", "Qml", "QmlModels", "QmlMeta", "QmlWorkerScript",
              "Quick", "OpenGL", "PrintSupport"]

ANDROID_COMPONENTS = [
    ("AndroidX Core 1.17.0, with its AndroidX (annotation, collection, lifecycle, "
     "versionedparcelable and others) and Kotlin standard library dependencies",
     "Apache-2.0", "The Android Open Source Project; JetBrains s.r.o. and Kotlin Programming Language contributors",
     "https://developer.android.com/jetpack/androidx"),
    ("LLVM libc++ (libc++_shared.so, from the Android NDK r27c)", "Apache-2.0 WITH LLVM-exception",
     "The LLVM Project contributors", "https://libcxx.llvm.org"),
]


def patchy_sections(patchy):
    text = (patchy / "NOTICE-THIRD-PARTY.md").read_text()
    parts = re.split(r"(?m)^## ", text)
    out = []
    for part in parts[1:]:
        title, _, body = part.partition("\n")
        title = title.strip()
        if title not in PATCHY_SECTIONS:
            continue
        body = body.strip()
        if title == "Bundled fonts (web build only)":
            body = ("Canvas bundles the open fonts Patchy's web build ships under `third_party/fonts-web/`, "
                    "because Android has none of the Windows families PSD text asks for (Arial, Times New "
                    "Roman, Calibri and so on). It leaves out Noto Sans JP, SC and TC, which Android "
                    "provides. Each family's `OFL.txt` ships with it, inside the app's resources.\n\n" +
                    body.split("\n\n", 1)[1])
        out.append((PATCHY_SECTIONS[title], body))
    return out


def qt_components(qt):
    found = {}
    extracted = {}
    for name in QT_SBOMS:
        path = qt / "sbom" / f"{name}-{qt.parent.name}.spdx.json"
        if not path.exists():
            sys.exit(f"notices: no {path}")
        data = json.loads(path.read_text())
        for info in data.get("hasExtractedLicensingInfos", []):
            extracted[info["licenseId"]] = info.get("extractedText", "")
        for pkg in data["packages"]:
            if "3rdparty" not in pkg["SPDXID"]:
                continue
            label = pkg["name"]
            label = re.sub(r"^.*_Attribution_", "", label)
            label = re.sub(r"^Bundled", "", label)
            lic = pkg.get("licenseConcluded") or pkg.get("licenseDeclared") or "NOASSERTION"
            if lic in ("NOASSERTION", "NONE"):
                lic = pkg.get("licenseDeclared") or lic
            version = pkg.get("versionInfo") or ""
            if version in ("unknown", "NOASSERTION"):
                version = ""
            key = (label.lower(), lic)
            copyright_text = pkg.get("copyrightText") or ""
            if copyright_text in ("NOASSERTION", "NONE"):
                copyright_text = ""
            found.setdefault(key, (label, version, lic, copyright_text, name))
    return sorted(found.values(), key=lambda c: c[0].lower()), extracted


def license_ids(expression):
    return [t for t in re.split(r"[\s()]+", expression)
            if t and t not in ("AND", "OR", "WITH") and t != "NOASSERTION"]


def license_text(spdx_id, extracted, offline):
    if spdx_id in extracted:
        return extracted[spdx_id]
    path = TEXTS / f"{spdx_id}.txt"
    if not path.exists():
        if offline:
            sys.exit(f"notices: no licence text for {spdx_id} in {TEXTS} (run without --offline)")
        with urllib.request.urlopen(SPDX_TEXT.format(spdx_id)) as reply:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(reply.read())
    return path.read_text()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--patchy", default=str(CACHE / "patchy"))
    parser.add_argument("--qt", default=None, help="Qt for Android kit (…/qt/<ver>/android_arm64_v8a)")
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--out", default=str(ROOT / "THIRD_PARTY_NOTICES.md"))
    args = parser.parse_args()
    upstream = dict(line.split("=", 1) for line in (ROOT / "UPSTREAM").read_text().splitlines()
                    if "=" in line and not line.startswith("#"))
    qt = pathlib.Path(args.qt or CACHE / "qt" / upstream["QT_VERSION"] / "android_arm64_v8a")
    patchy = pathlib.Path(args.patchy)

    used = {"MIT", "LGPL-3.0-only", "GPL-3.0-only"} | set(PATCHY_SECTION_LICENSES)
    lines = [
        "# Third-party notices",
        "",
        "Canvas is [Patchy](https://github.com/SethRobinson/Patchy) built for Android with the patches in",
        "[github.com/kuscher/canvas](https://github.com/kuscher/canvas). This file lists every",
        "component inside the Canvas app, with its licence; the full licence texts follow at the end.",
        "It is generated by `tools/notices.py`.",
        "",
        "## Canvas",
        "",
        "Canvas's own code (the Android activity and helpers, the build tools and the patches to Patchy)",
        "is Copyright (c) 2026 Fika Labs, under the MIT License. Canvas is a personal passion",
        "project, not affiliated with or endorsed by any employer, and not made or endorsed by Patchy's",
        "author.",
        "",
        "Every Canvas release on GitHub also carries the complete source of what is in the app: Canvas",
        "with the patched Patchy source (including its bundled libraries), and the source archives of",
        "the Qt modules the app ships.",
        "",
        "## Patchy",
        "",
        f"Patchy {upstream['PATCHY_TAG'].lstrip('v')} (https://github.com/SethRobinson/Patchy), Copyright (c) 2026",
        "Seth A. Robinson, under the MIT License. Patchy's own third-party parts that the Android build",
        "ships are listed below, in the words of Patchy's `NOTICE-THIRD-PARTY.md`.",
        "",
    ]
    for title, body in patchy_sections(patchy):
        lines += [f"### {title}", "", body, ""]

    components, extracted = qt_components(qt)
    lines += [
        f"## Qt {qt.parent.name}",
        "",
        "Canvas dynamically links these Qt modules, which The Qt Company licenses under the GNU Lesser",
        "General Public License version 3 (LGPL-3.0-only), among other options: " +
        ", ".join(f"Qt {m}" for m in QT_MODULES) + ", together with Qt's Android platform plugin,",
        "the Android style, the SVG icon engine and the GIF, ICO, ICNS, JPEG, SVG, TGA, TIFF, WBMP",
        "and WebP image-format plugins, and Qt's Java bindings for Android. They are unmodified",
        "builds from Qt's servers, shipped as separate shared libraries in the APK, so they can be",
        "replaced. Qt's source code: https://download.qt.io/official_releases/qt/"
        f"{qt.parent.name.rsplit('.', 1)[0]}/{qt.parent.name}/single/ (and code.qt.io).",
        "",
        "Third-party components inside those Qt modules, as Qt's SBOMs declare them (the SBOMs",
        "also name a few build- and test-only parts; they are listed as declared):",
        "",
        "| Component | Version | Licence |",
        "| --- | --- | --- |",
    ]
    for label, version, lic, _copyright, _module in components:
        lines.append(f"| {label} | {version} | {lic} |")
        used.update(license_ids(lic))
    lines.append("")

    lines += ["## Android runtime", ""]
    for name, lic, holder, url in ANDROID_COMPONENTS:
        lines += [f"- {name}: {lic}. {holder}. {url}"]
        used.update(license_ids(lic))
    lines.append("")

    lines += ["## Licence texts", ""]
    for spdx_id in sorted(used):
        lines += [f"### {spdx_id}", "", "```", license_text(spdx_id, extracted, args.offline).strip(), "```", ""]

    pathlib.Path(args.out).write_text("\n".join(lines))
    print(f"{args.out}: {len(components)} Qt components, {len(used)} licences")


if __name__ == "__main__":
    main()
