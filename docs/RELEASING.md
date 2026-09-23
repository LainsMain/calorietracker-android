# Building and releasing

Requirements: JDK 17, Android SDK Platform 37.0, Build Tools 36.0.0. Android Studio Panda 3
or newer with AGP 9.1.1 support. The Gradle wrapper downloads Gradle 9.3.1.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

Select a test emulator explicitly if a personal device is connected. Debug and release
APKs use different signatures and cannot update one another.

## Release identity

The initial owner-held identity is outside the repository at
`~/.local/share/calorietracker/signing/` (directory mode 0700, files 0600).
Back up **both** `release.jks` and `credentials.json` to a secure owner-controlled location.
Losing the signing key prevents in-place updates to installed apps. Never commit these files.

`python scripts/create_signing.py` creates an identity only when no credentials exist.
`python scripts/build_release.py 1.2.1 6` builds locally using the owner-held identity.
Other machines can provide `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD` and
`SIGNING_KEY_PASSWORD` environment variables directly to `./gradlew :app:assembleRelease`.
Alias: `calorietracker`.

GitHub Actions uses repository secrets `SIGNING_STORE_BASE64`, `SIGNING_STORE_PASSWORD`,
and `SIGNING_KEY_PASSWORD`. Signing credentials are not provided to pull-request jobs.
Only repository maintainers should be able to push release tags or alter release workflows.

## New version

1. Increase `version-code.txt` monotonically. Update the default version name in the app
   build file and `docs/RELEASE_NOTES.md`.
2. Run local checks, commit and push main; review CI and emulator results.
3. Push a matching `vX.Y.Z` tag. The release workflow reruns checks, builds and signs the APK,
   generates `update.json`/`SHA256SUMS`, verifies the APK and publishes GitHub Releases.
4. Install over a prior signed version on a test device, checking diary preservation and
   denied/cancelled installation paths. Never test with the only copy of personal records.

Published assets are `CalorieTracker-X.Y.Z.apk`, `update.json`, and `SHA256SUMS`. The app
checks the latest release at most once daily, or immediately through Settings.
