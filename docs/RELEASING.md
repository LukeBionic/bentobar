# Releasing BentoBar

A release is made by pushing a tag. GitHub Actions then builds, signs and publishes it:

- a **GitHub Release** with the signed `BentoBar.apk` (the README's download button points at
  `releases/latest/download/BentoBar.apk`, so this name is fixed) and `SHA256SUMS`;
- the same build as an App Bundle on **Google Play**, as a draft on the closed-testing track.

Nobody needs the signing key on their machine for this.

**Every release must be signed with the BentoBar release key** (alias `bentobar`, certificate SHA-256
`AA:07:4B:07:48:F4:9E:21:96:7C:EF:C8:BE:EC:09:91:86:14:7D:0F:F2:FC:C8:91:C4:FC:72:18:BD:90:CE:93`).
Android only installs an update over an existing app when both are signed with the same key; a
release signed with anything else makes everyone uninstall first (and lose their layout). The
workflow checks the APK and the bundle against this certificate and stops if either differs.

## Steps

1. On `main`: bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`. Play refuses a
   version code it has seen before.
2. Add `docs/release-notes/<version>.md`: the text of the GitHub release. Keep the shape of the
   last one (a line on what BentoBar is, Install, What's new, Verify). Put the same "What's new"
   under a new heading in `CHANGELOG.md`.
3. Write Play's "What's new" in the store kit's `release-notes.txt`: plain text, 500 characters at
   most. The store kit (listing texts, graphics and the answers given in the Play Console's forms) is
   kept privately, outside this repo.
4. Commit and push.
5. Tag that commit and push the tag:

```bash
git tag v<version> && git push origin v<version>
```

## On one device before a tag

CI cannot see what only hardware shows. Install the release candidate on one Googlebook (a release build, not debug)
and look at each of these; AGENTS.md "Things learned the hard way" says why. Run it again on every release candidate.

- **Turning it on.** First open shows the disclosure with two buttons; Agree then leads to Accessibility settings, and
  turning the service on puts the strip in the status bar. Declining leaves the service off.
- **The strip's colour.** Light and dark icons on the strip match the status bar's, with a light window and a dark one
  maximized, and after restoring a window.
- **Unlock.** Lock and unlock: within about ten seconds the strip's colour matches the bar's again (it once stayed white
  after the lock screen's light icons).
- **Full screen.** An app in full screen hides the strip when the bar hides (about 2 s after the app goes full screen)
  and brings it back when the bar returns; a shade or the keyboard's full-screen key behave the same.
- **The empty strip.** With every item hidden or empty there is no stray window and no invisible overlay (Play Protect
  flagged one); Settings shows the strip as empty.
- **Play Protect.** After the install, Safety Center (Settings > Security) shows no "App displays over other apps" or
  other warning for BentoBar.
- **Menus.** The ‹ chevron and an item open their menus at the right width (not cut off, not pushed under the chevron);
  a click outside closes them; the glass and its shadow sit together.
- **Live Update chip.** A timer or a meeting shows its chip with BentoBar's settings window closed.
- **Items that tick.** A clock and a network speed item do not make neighbours move; the bar's CPU use stays low
  (about 1% of one core on a release build).
- **Keep awake and timers.** Keep awake keeps the screen on, ends when switched off, and a timer rings once and stops.
- **Update over the old release.** Installed over the previous release (same key), the layout and the accessibility
  switch are still there.
- **Online services.** With them off nothing is asked; weather shows the placeholder, not an error.

## What the tag does

`.github/workflows/release.yml` runs on every `v*` tag. It:

1. checks that `docs/release-notes/<version>.md` exists and that `versionName` equals the tag;
2. runs the unit tests and builds the signed APK and the signed bundle (.aab);
3. checks both against the BentoBar certificate;
4. publishes the GitHub release "BentoBar \<version\>" with `BentoBar.apk`, `SHA256SUMS` and the
   notes file as its text;
5. uploads the bundle to Google Play's closed-testing track as a **draft** (`tools/play-upload.mjs`),
   without notes: the "What's new" text is added from the store kit when the release is sent.
   Releases that are live or rolling out stay as they are.

A draft isn't served to anyone and isn't reviewed. **Someone still presses "Send for review" in the
Play Console**; nothing goes to review on its own.

If only the Play job fails, re-run that job from the Actions tab. A version code that is on Play
already is left alone, so a re-run uploads nothing twice.

**After Google has rejected an update**, Play refuses any commit that could send itself for review
("Changes cannot be sent for review automatically"), until the next changes have been sent from the
Play Console. The upload then commits its draft with `changesNotSentForReview=true`, which changes
nothing about the draft. (0.8's tag ran before this was known: its bundle was put on Play by hand,
from the workflow's `bundle` artifact.)

## A release for GitHub alone

To publish a version on GitHub without putting anything on Google Play, switch the Play job off
for that tag and on again after it:

```bash
gh variable set PLAY_UPLOAD --body off
git tag v<version> && git push origin v<version>
# when the run is done:
gh variable delete PLAY_UPLOAD
```

The run builds, checks and publishes the GitHub release as usual; the Play job shows as skipped.
That version can go to Play later from the run's `bundle` artifact, or with the next tag.

## A dry run

Actions tab › Release › **Run workflow** (on `main`), or `gh workflow run release.yml --ref main`.
It runs the same tests, signed build and certificate checks and publishes nothing: no GitHub
release, no upload. The Play job only checks that the Play key works and prints what is on the
closed-testing track. Do this after changing the workflow or the build.

## Where the keys live

- **The signing key:** secrets of the GitHub environment `release` (`SIGNING_KEYSTORE_B64`, the
  keystore in base64, and `SIGNING_KEYSTORE_PASS`). **The Play key** (a service account that can
  upload to Play): `PLAY_SERVICE_ACCOUNT_JSON` in the environment `play`.
- Both environments can be used from `main` and `v*` tags only, so pull requests and forks never
  get the secrets. The job that holds the signing key runs only GitHub's own actions, pinned to
  exact commits, and the Play job never sees the signing key.
- Anyone with write access can push a tag, and so can release. Give write access only to people
  you'd trust with the key.
- **Backup:** the keystore and its password are backed up privately, outside the repo. Agents
  never need the key file.
- The key is never committed (`.gitignore` covers `*.jks` and `*.keystore`).

## By hand, on a machine that has the key

The fallback. With the key at `~/.config/bentobar/keystore.jks` and its password in
`~/.config/bentobar/keystore.pass`, `app/build.gradle.kts` signs release builds by itself:

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:bundleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk | grep SHA-256   # must be aa074b07…bd90ce93
```

The APK is `app/build/outputs/apk/release/app-release.apk` (publish it as `BentoBar.apk`, with
`sha256sum BentoBar.apk > SHA256SUMS`), the bundle `app/build/outputs/bundle/release/app-release.aab`.
If the build makes `app-release-unsigned.apk` instead, the key wasn't found: stop, don't publish an
unsigned or differently signed APK.

## Checking a release

```bash
sha256sum -c SHA256SUMS
apksigner verify --print-certs BentoBar.apk | grep SHA-256
```

If `apksigner` isn't on PATH, it's in `$ANDROID_HOME/build-tools/<version>/`.
