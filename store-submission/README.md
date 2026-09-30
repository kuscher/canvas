# Google Play submission kit

Everything the Play Console asks for when publishing Canvas, ready to copy or upload (made on 30 September 2026,
following Summa's kit). The app exists in the Play Console as a draft: io.github.kuscher.canvas, app id 4975391581258557149, developer
account Fika Labs (7424304467248438473).

## What's here

| Play Console field | File | Limit / spec |
|---|---|---|
| App name | [listing/en-US/title.txt](listing/en-US/title.txt) | 30 characters |
| Short description | [listing/en-US/short-description.txt](listing/en-US/short-description.txt) | 80 characters |
| Full description | [listing/en-US/full-description.txt](listing/en-US/full-description.txt) | 4,000 characters |
| Release notes ("What's new") | [listing/en-US/release-notes.txt](listing/en-US/release-notes.txt) | 500 characters |
| App icon | [graphics/icon-512.png](graphics/icon-512.png) | 512 × 512 PNG, full square (Play rounds the corners), drawn from the launcher icon's layers |
| Feature graphic | [graphics/feature-graphic.png](graphics/feature-graphic.png) | 1024 × 500, 24-bit PNG |
| Screenshots | [graphics/large-screen/](graphics/large-screen) (4) | 1920 × 1080 (16:9), 24-bit PNG. Used for phone, 7-inch, 10-inch and Chromebook |
| Store settings, contact, category | [forms/store-settings.md](forms/store-settings.md) | |
| Privacy policy | https://googlebook.studio/privacy/canvas | public, outside googlebook.studio's invite gate |
| Data safety | [forms/data-safety.md](forms/data-safety.md) | "No data collected" |
| Content rating (IARC) | [forms/content-rating.md](forms/content-rating.md) | expected: Everyone / PEGI 3 |
| Other App content declarations | [forms/app-content.md](forms/app-content.md) | |

The listing text avoids what Play's metadata policy rules out: rankings or superlatives, promotional words, testimonials,
emoji, calls to action and other companies' app names. The screenshots are Canvas's own README images with a caption,
made with `scripts/play/graphics.mjs` in kuscher/googlebook-tech.

## Steps

1. **App signing (decide once, it can't be undone).** Recommended, as for Summa: *Use existing app signing key* and upload
   the Canvas release key (the one `tools/release.sh` checks) with Google's PEPK tool, so the Play build and the APKs on GitHub have the same signature and people can
   move between them without uninstalling. The same key is the upload key.
2. **Build the bundle** (Play only takes .aab files): `./cv build universal` makes the APK for GitHub. Play takes only an App Bundle: build the same universal configuration with Qt's `aab` CMake target (`cmake --build <build dir> --target aab`), signed with the Canvas release key (the same one `tools/release.sh` checks, SHA-256 `e0f8b133…`). Play then serves each Googlebook only its own ABI. Each upload needs a higher version code than the last
   (VERSION (`VERSION_CODE`)).
3. **Closed test first.** The developer account is a personal one: before production, a closed test with at least 12
   testers opted in for 14 days in a row.
4. **Store listing, store settings and App content:** filled in from these files on 30 September 2026.
5. **Release:** add the bundle to the closed testing track, paste `release-notes.txt`, send for review.
