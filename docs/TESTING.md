# Verification

## Automated checks

`./gradlew :app:testDebugUnitTest :app:lintDebug` validates the deterministic engine and
Android integration contracts. The tests cover recipe yields and water loss, immutable
food/recipe snapshots, explicit unit conversion, unknown nutrients, historical plan
selection, macro reconciliation, adult/restricted-profile boundaries, Brussels DST,
guided target bounds, weekly recurrence, evidence thresholds, robust slope/noise holds,
deadband and adjustment cap, backup authentication, OFF prepared/liquid products, safe
Markdown, fragmented thinking/tool streams, bounded context, and workout deduplication.

`ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest` exercises real
Keystore/Room encryption, idempotent proposal and weekly approval, message deletion/summary
invalidation, backup replacement and invalid-backup recovery. The Compose journeys visit
all five destinations, render a Markdown reply and imported run, exercise proposal apply/undo,
open guided planning and weekly review, capture screenshots, create a custom food, and log it.
The Health Connect test seeds test-only steps/weight records, compares aggregation against
platform source-priority behaviour, repeats sync, then deletes source records and checks
reconciliation. It runs on API 34+; debug-only write permissions are absent from releases.

CI runs builds/lint/unit tests and emulator instrumentation on API 29 and API 36.
Local development additionally uses the API 36.1 emulator. No personal physical device
is required, and test execution should explicitly select its emulator serial.

## External integration checks

Open Food Facts was queried live for Belgian branded products and barcode responses.
Missing product/unknown nutrient handling is covered separately. This is sampling, not a
claim that every Belgian retailer/product is present.

DeepSeek protocol integration is implemented against its documented chat-completions,
thinking, vision and function-tool interface. Fragmented reasoning/tool deltas are tested
locally; reasoning is replayed when required and never rendered. A funded user API key is required for a real paid
model call. No key is bundled or borrowed from local unrelated projects. Live model
quality, images and tool decisions must be assessed with the user's configured key.

Health Connect import depends on which fitness apps write records and how their source
priorities are configured. Emulator fixtures cannot certify every watch/provider pairing.
Camera scanning is implemented with CameraX and bundled ML Kit; real packaging, camera
focus and lighting require a physical-device check.

## Release checks

Use the owner-held signing identity to build an older version and the candidate release.
Install the older version, save a plan/diary record, then install the higher version without
uninstalling and verify preservation. Check APK certificates with `apksigner verify`.
The updater verifies byte count, SHA-256, full APK cryptographic signature, package name,
version and installed signing identity before invoking Android's installer.

Keep cancellation/retry, unknown-sources denial, corrupt downloads and certificate changes
in the release regression checklist. Never bypass Android installation confirmation.
