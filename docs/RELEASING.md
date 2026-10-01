# Releasing Canvas

A release is a GitHub Release with the signed `Canvas.apk` (the README's download button points at
`releases/latest/download/Canvas.apk`, so this name is fixed), the source the licenses ask for
(`Canvas-<version>-source.tar.gz` and Qt's `qt*-everywhere-src-*.tar.xz` archives) and `SHA256SUMS`.
The same tag also puts the Play bundle on Google Play as a draft.

**The short version, for people and agents:** nobody needs the key file. Bump the version, write the
notes, push a tag `v<version>`, and GitHub builds, signs and publishes.

**Every release must be signed with the Canvas release key** (alias `canvas`, certificate SHA-256
`4F:E4:24:41:83:EA:D0:CA:24:1F:AE:39:4F:1F:BF:4A:06:2B:C5:89:70:A7:20:82:63:85:3D:18:74:2E:FF:03`).
Android only installs an update over an existing app when both are signed with the same key; a
release signed with anything else makes everyone uninstall first. The key lives in the repo's
`release` environment on GitHub and with the maintainer in `~/.config/canvas/` (`keystore.jks`,
`keystore.pass`), backed up with its password to private storage (folder a private folder). It is the same key Google Play uses for Canvas, and it is
never committed (`.gitignore` covers `*.jks`, `*.keystore`, `*.pass`).

## Steps

1. On `main`: bump `VERSION_NAME` and `VERSION_CODE` (+1) in `VERSION`.
2. Add a `## <version> (<date>)` section to `CHANGELOG.md` (what's new, in plain words; the release's
   notes are this section). Put Google Play's "What's new" text (500 characters at most) in
   `store-submission/listing/en-US/release-notes.txt`.
3. Commit and push, then tag the commit and push the tag:

```sh
git tag v0.2 && git push origin v0.2
```

The tag runs `.github/workflows/release.yml`, which:
- checks that `VERSION` says the tag's version, that `CHANGELOG.md` has a section for it, and that
  the Play text fits;
- runs the build of `.github/workflows/build.yml` (the universal APK and the App Bundle, unsigned;
  this long job never sees a key) and its launch check in the Android 16 emulator;
- makes the source archives;
- signs the APK and the bundle in a short job of its own, and refuses to publish if either isn't
  signed with the Canvas certificate, or if the version or the ABIs (arm64-v8a and x86_64) are wrong;
- publishes the GitHub release with `Canvas.apk`, the source archives and `SHA256SUMS`;
- uploads the bundle to Google Play's closed-testing track as a **draft** release
  (`tools/play-upload.mjs`), with the Play text. What is live there stays live.

A run takes about half an hour: some 25 minutes for the build, 3 for the launch check, and a minute
or two to sign and publish.

**Google Play, the last step by hand:** a draft is not reviewed or served. In the Play Console, open
Canvas › Test and release › Closed testing › the draft › Next › Save, then Publishing overview › Send
for review. Nothing goes to review on its own.

To test without publishing, press **Run workflow** on the Actions tab (Release), or run
`gh workflow run release.yml --ref main`: the same build, signing and certificate checks, a check
that the Play key works, and nothing published.

If a tag's run fails after the GitHub release was made (for example at the Play step), fix the cause
and re-run the failed job. Re-running everything is safe too: a release that is there already is left
as it is, and re-uploading a version code that is already on Play does nothing.

### Where the secrets are

Two GitHub environments that only `main` and `v*` tags can use: `release` holds the signing key
(`SIGNING_KEYSTORE_B64`, the keystore in base64, and `SIGNING_KEYSTORE_PASS`), and `play` holds
the Google Play key (`PLAY_SERVICE_ACCOUNT_JSON`). Workflows from forks and pull requests never get
them. Anyone with write access can push a tag, and so could also read the key through a workflow of
their own: give write access only to people you'd trust with the key. The job that holds the key
runs only GitHub's own actions, pinned to exact commits, and only signs and checks; the build, which
downloads Qt, the NDK and Patchy, runs before it in a job without secrets.

Restoring them (for example after a key restore from the backup):
- `base64 < ~/.config/canvas/keystore.jks | tr -d '\n' | gh secret set SIGNING_KEYSTORE_B64 --env release`
- `gh secret set SIGNING_KEYSTORE_PASS --env release < ~/.config/canvas/keystore.pass`
- `gh secret set PLAY_SERVICE_ACCOUNT_JSON --env play < play-service-account.json` (the Play
  Console service account's key; the maintainer has it)

### By hand (fallback, on a machine that has the key)

The local build needs the toolchain from `./cv setup` (an arm64 Linux machine such as the
Googlebook's Linux Terminal) and the key in `~/.config/canvas/`.

```sh
./cv build universal        # build/Canvas-universal.apk, signed with the key in ~/.config/canvas
tools/release.sh            # checks signature, version, ABIs and a clean tree → dist/v<version>/
tools/release.sh --publish  # … and tag v<version>, push the tag, create the GitHub release
```

`tools/release.sh` refuses to continue if the APK isn't signed with the Canvas certificate, doesn't
carry `VERSION`'s version or both ABIs, or the tree has uncommitted changes. It also needs Qt's
source archives in `~/.cache/canvas/qt-src/archives` (it prints the `aqt install-src` command when
they are missing).

`--publish` pushes the tag, so the release workflow runs as well: it finds the release already
there, leaves it, and still puts the bundle on Google Play as a draft.

On a machine without the toolchain, every build's unsigned APK and bundle are artifacts of the APK
workflow (`gh run download <run> --name canvas-apk`, and `--name canvas-aab`); sign them the way
the `Sign` step of `release.yml` does.

## Google Play

Play takes an Android App Bundle instead of an APK. The release workflow builds, signs and uploads
it (see above). By hand, Qt's `aab` target makes the unsigned bundle, `jarsigner` signs it with the
same key (it serves as the upload key), and `tools/play-upload.mjs` puts it on Play as a draft:

```sh
cmake --build ~/.cache/canvas/build-universal --target aab
jarsigner -keystore ~/.config/canvas/keystore.jks -storepass:file ~/.config/canvas/keystore.pass \
  -sigalg SHA256withRSA -digestalg SHA-256 -signedjar Canvas.aab <the release .aab> canvas
PLAY_SERVICE_ACCOUNT_FILE=<key.json> node tools/play-upload.mjs io.github.kuscher.canvas Canvas.aab \
  store-submission/listing/en-US/release-notes.txt --name <version>
```

The listing, graphics and policy answers are in `store-submission/` (see its README, including the
one-time app-signing choice).

## Checking a release by hand

```sh
apksigner verify --print-certs Canvas.apk | grep SHA-256
sha256sum -c SHA256SUMS
```

If `apksigner` isn't on PATH, it's in `$ANDROID_HOME/build-tools/<version>/`.
