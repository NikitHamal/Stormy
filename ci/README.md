# CI workflow

`android.yml.txt` is the GitHub Actions workflow for this project.

**To install it:**

```bash
mv ci/android.yml.txt .github/workflows/android.yml
git add -A && git commit -m "ci: update workflow" && git push
```

It is parked here rather than in `.github/workflows/` because the GitHub
connector used to open this branch has no `workflow` OAuth scope: any API
write touching `.github/workflows/` is rejected with a 404. Renaming the file
locally, where your own credentials apply, is all that is needed. The contents
are a complete, valid workflow — nothing else needs editing.

## What it fixes

Against the eleven annotations reported on the original run:

| Annotation | Fix |
|---|---|
| Node 20 deprecation (5 actions) | checkout@v7, setup-java@v6, upload-artifact@v7, setup-gradle@v6 — all `using: node24`. The separate `actions/cache@v4` is gone entirely. |
| 7 × "Failed to save cache entry" | Root cause was two owners for one cache. `setup-gradle` already caches the whole Gradle home; a manual `actions/cache` step covered the same paths, so the two raced on save. The manual step is removed — one owner only. |
| "Failed to restore … responded with 400" | Same cause; same fix. |
| `setup-java v4 is deprecated` | Now v6. |
| `ubuntu-latest` migrating to Ubuntu 26 on 2026-10-19 | Pinned to `ubuntu-24.04`, so the toolchain does not change underneath the build on that date. |

## Other changes

- **JDK 21, not 17.** AGP 9.4's bundled lint crashes on JDK 17 inside UAST
  (`AsyncExecutionService.getService must not return null`), which fails
  `assembleRelease` at `lintVitalAnalyzeRelease`. This is not optional.
- Runs `testDebugUnitTest` before the APK builds.
- `core/**` added to the path filter, so changes to the new modules trigger CI.
- `concurrency` cancellation, wrapper validation and `if-no-files-found: error`
  on uploads.
- Triggers on branch pushes only (no `pull_request` trigger, so PRs from a
  branch in this repo no longer run the build twice); release APK only — the
  debug APK build and the separate lint job were removed.

## Screenshots (`screenshots.yml.txt`)

JVM screenshots of the device agent's island and screens (Robolectric +
Roborazzi, `app/src/test/java/com/oro/app/screenshots/`). Two ways to run them:

- **Push to a `shots/**` branch** (or `ci/shots`). The normal Android CI then
  runs only the screenshot tests in its unit-test step and prints every PNG as
  base64 in the job log, every line prefixed `ORO|` (see
  `app/build.gradle.kts`). Works with no workflow change, so it is what agents
  use through the connector.
- **Install `screenshots.yml.txt`** as `.github/workflows/screenshots.yml` for a
  manual "UI screenshots" button that also uploads the PNGs as an artifact.

Locally: `./gradlew :app:testDebugUnitTest -Poro.screenshots=true`, images in
`app/build/outputs/roborazzi/`.
