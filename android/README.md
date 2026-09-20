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
- `onboarding/OnboardingScreen.kt` — twin-creation screen: a single
  "paste anything about yourself" text-dump box plus an optional Instagram
  handle field and an optional LinkedIn PDF upload. The text dump goes to
  the `submitContext` callable; Instagram and LinkedIn go to
  `importSocialContext` (public web search via Parallel for Instagram, PDF
  text extraction for LinkedIn — no OAuth for either).
- `notifications/TwinMessagingService.kt` — FCM receiver that shows the
  match notification (photo, name, one specific reason, "Say hi" action).
- `match/MatchScreen.kt` — the handoff screen: matched person + reason +
  a single confirm action. No auto-generated intro message is ever sent.
- `ui/HomeScreen.kt` — the idle screen: an animated character
  (`ui/ListeningAvatar.kt`, ported from `design/Kindred App.dc.html`'s
  Ambient screen) plus a live feed of recent negotiations. Tapping a
  confirmed match opens MatchScreen; tapping a non-match opens
  `ui/NegotiationDetailScreen.kt` (full transcript + score + reason).
  BLE is the sole proximity trigger — there's no manual check-in fallback
  in the UI (removed once real BLE proximity was confirmed working; see
  `checkin/CheckinRepository.kt`, still used directly by
  BleProximityService).
- `MainActivity.kt` — Compose navigation (Welcome → Sign In/Up → Onboarding
  or Home → Match/Negotiation detail), BLE permission requests, and
  starting the foreground service (only after a successful sign-in/up).
- `auth/` — real accounts (email/password + Google Sign-In) via Firebase
  Auth, replacing the earlier anonymous-only model. "Build your twin" on
  the welcome screen leads to Sign In; "I've already got one" leads to
  Sign Up. Sign-in (email/password or Google) checks whether this account
  already has a completed twin profile (`twins/{uid}.onboardingComplete`)
  and routes to Home if so, Onboarding if not; a fresh email/password
  sign-up always lands in Onboarding since the account is guaranteed new.

## Before this builds or runs

1. **`app/google-services.json`** — checked into the repo (project
   `blades-a38f5`, applicationId `com.blade.app`). Not treated as a secret:
   Firebase client config is meant to ship inside the built app and is
   protected by Firestore/App Check security rules, not by hiding the
   file — fine to commit in this private repo. Anonymous Auth, Firestore,
   Cloud Functions, and Cloud Messaging are enabled on that project.
2. **Real BLE service UUID** — `ble/BleConstants.kt` has a placeholder
   128-bit UUID. Generate a real random one (`uuidgen`) and swap it in. All
   installs of the app must share the same UUID.
3. **Instagram — no app-side setup needed.** The Instagram field in
   `OnboardingScreen.kt` is a plain public web search (via Parallel — see
   `functions/src/lib/parallel.ts`), not an OAuth flow, so there's no
   Meta App Dashboard product to configure and no client ID to ship in the
   app. Only the server side needs a `PARALLEL_API_KEY` secret — see
   `functions/README.md`.
4. **Firebase Auth sign-in providers** — in the Firebase console
   (Authentication → Sign-in method): enable **Email/Password**, and
   enable **Google** (this also generates the "Web client ID" — copy it
   into `res/values/strings.xml`'s `google_web_client_id`, replacing the
   `TODO_...` placeholder). Without both of these, Sign In/Sign Up will
   fail — see `auth/AuthManager.kt`.
5. **Gradle wrapper jar** — `gradle/wrapper/gradle-wrapper.properties` is
   present but the wrapper jar binary is not checked in from this scaffold.
   Run `gradle wrapper` once (with a local Gradle 8.7 install) to generate
   `gradle/wrapper/gradle-wrapper.jar` and `gradlew`/`gradlew.bat`, or open
   the project directly in Android Studio, which will bootstrap the wrapper
   for you.
6. **Backend** — `submitContext`, `importSocialContext`, `onCheckin`,
   `negotiateTwins`, and `notifyMatch` all exist in `../functions/` and
   deploy to the same Firebase project. See `functions/README.md` for the
   secrets to set (`META_MODEL_API_KEY`, `PARALLEL_API_KEY`) before
   they'll actually run. `TwinMessagingService.onNewToken` still has a TODO
   to persist the FCM token against the twin's Firestore doc — wire that up
   so `notifyMatch` has somewhere to send pushes.

## Known scaffold gaps (fine for a hackathon prototype, flagged for later)

- `MainActivity.onNewIntent` doesn't yet thread a second notification tap
  (while the app is already open) into the already-composed screen state —
  noted as a TODO there. Fine for a demo where the app is normally opened
  fresh from the notification.
- No unit/instrumented tests, intentionally, per project scope.
- BLE background behavior on `onTaskRemoved` (app swiped from Recents) is a
  deliberate no-op (keep running) but has NOT been verified on real
  hardware — OEM battery managers can kill foreground services anyway.
  Test this on the actual demo device before HackMIT.
