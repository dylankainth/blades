# Digital Twins — Android app

Native Android scaffold (Kotlin, Jetpack Compose, minSdk 31 / Android 12)
for the HackMIT digital-twins project. See the root `CLAUDE.md` for the
full product context.

## What's here

- `ble/BleProximityService.kt` — foreground service that both advertises
  this device's twinId over BLE (broadcast-only, no GATT) and scans for
  other twins' advertisements on the same service UUID. On detection,
  writes a checkin to `checkins/{locationId}/people/{twinId}` for both
  twins via `checkin/CheckinRepository.kt`.
- `onboarding/OnboardingScreen.kt` — conversational twin-creation chat UI,
  backed by the `onboardingChat` Firebase Functions callable, plus a
  Facebook Login button scoped to `public_profile` only.
- `notifications/TwinMessagingService.kt` — FCM receiver that shows the
  match notification (photo, name, one specific reason, "Say hi" action).
- `match/MatchScreen.kt` — the handoff screen: matched person + reason +
  a single confirm action. No auto-generated intro message is ever sent.
- `checkin/CheckinScreen.kt` — manual "I'm at booth X" fallback, writing to
  the same Firestore path as the BLE service, for demo-day reliability.
- `MainActivity.kt` — Compose navigation (Onboarding → Home → Match/Checkin),
  anonymous Firebase Auth for a stable per-device twinId, BLE permission
  requests, and starting the foreground service.

## Before this builds or runs

1. **`app/google-services.json`** — does not exist yet (see
   `app/google-services.json.TODO` for exact instructions). Register a
   Firebase Android app with applicationId `com.hackmit.twins`, download the
   real config, and drop it in at `app/google-services.json`. Enable
   Anonymous Auth, Firestore, Cloud Functions, and Cloud Messaging in that
   Firebase project.
2. **Real BLE service UUID** — `ble/BleConstants.kt` has a placeholder
   128-bit UUID. Generate a real random one (`uuidgen`) and swap it in. All
   installs of the app must share the same UUID.
3. **Facebook App ID / Client Token** — `res/values/strings.xml` has
   placeholder values for `facebook_app_id` / `facebook_client_token`.
   Replace with real values from developers.facebook.com. Login is scoped
   to `public_profile` only (name + photo, no email/friends/posting).
4. **Gradle wrapper jar** — `gradle/wrapper/gradle-wrapper.properties` is
   present but the wrapper jar binary is not checked in from this scaffold.
   Run `gradle wrapper` once (with a local Gradle 8.7 install) to generate
   `gradle/wrapper/gradle-wrapper.jar` and `gradlew`/`gradlew.bat`, or open
   the project directly in Android Studio, which will bootstrap the wrapper
   for you.
5. **Backend pieces this app assumes exist** (not part of this scaffold):
   - `onboardingChat` HTTPS callable Cloud Function.
   - A Cloud Function watching `checkins/{locationId}/people/*` that runs
     the matching engine and sends the FCM push with `matchedTwinId`,
     `matchedName`, `matchedPhotoUrl`, `reason` in the data payload.
   - Somewhere to store each twin's FCM token against its twinId (see the
     TODO in `TwinMessagingService.onNewToken`).

## Known scaffold gaps (fine for a hackathon prototype, flagged for later)

- No image caching/upload pipeline for the Facebook profile photo — only
  the Facebook userId is captured today; fetching name/photo via a
  `GraphRequest` is a TODO in `OnboardingScreen.kt`.
- `MainActivity.onNewIntent` doesn't yet thread a second notification tap
  (while the app is already open) into the already-composed screen state —
  noted as a TODO there. Fine for a demo where the app is normally opened
  fresh from the notification.
- No unit/instrumented tests, intentionally, per project scope.
- BLE background behavior on `onTaskRemoved` (app swiped from Recents) is a
  deliberate no-op (keep running) but has NOT been verified on real
  hardware — OEM battery managers can kill foreground services anyway.
  Test this on the actual demo device before HackMIT.
