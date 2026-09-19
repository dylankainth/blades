# Digital Twins backend — Firebase Cloud Functions

Firestore + Cloud Functions v2 (Cloud Run-backed) + FCM backend for the
proximity-gated digital-twin matchmaking prototype. See the repo root
`CLAUDE.md` for full product context and guardrails.

## Layout

```
functions/
  src/
    index.ts            exports every deployable function
    onboardingChat.ts    callable: conversational twin-creation chat turn
    onCheckin.ts         Firestore trigger: checkins/{locationId}/people/{twinId}
    negotiateTwins.ts    twin-to-twin negotiation (callable + internal helper)
    notifyMatch.ts       Firestore trigger: matches/{matchId} -> FCM push
    types.ts             shared Firestore document shapes
    lib/
      admin.ts           shared Firebase Admin SDK instance (db, messaging)
      secrets.ts         Cloud Functions v2 secret/param declarations
      metaModel.ts        fetch-based client for the Meta Model API
```

## Firestore data model

- `twins/{twinId}` — profile: name, photoUrl, summary/interests extracted
  from onboarding, FCM tokens.
- `onboarding_sessions/{twinId}` — chat turn history (`turns: OnboardingTurn[]`).
- `checkins/{locationId}/people/{twinId}` — presence docs; a write here is
  the (faked) proximity signal that triggers matching.
- `matches/{matchId}` — the two twin IDs, negotiation transcript, final
  plain-language reason, and status (`proposed` | `negotiating` |
  `confirmed` | `dismissed`). `matchId` is the two twin IDs sorted and
  joined with `_`.

## Functions

| Function | Trigger | Purpose |
|---|---|---|
| `onboardingChat` | callable | One turn of the ~90s onboarding interview via the Meta Model API (Muse Spark). Stateless per call — reads/writes the transcript in Firestore. |
| `onCheckin` | `checkins/{locationId}/people/{twinId}` write | Finds other twins checked in at the same location in the last ~20 min and kicks off negotiation with each. |
| `negotiateTwins` | callable (also called directly from `onCheckin`) | Runs the twin-to-twin negotiation over the Meta Model API and writes the transcript + outcome to `matches/{matchId}`. |
| `notifyMatch` | `matches/{matchId}` write | On transition to `status: "confirmed"`, sends an FCM push to both twins with a "say hi?" prompt. Never auto-messages or auto-schedules. |

All functions are Cloud Functions **v2** (`firebase-functions/v2/...`),
Cloud Run-backed, with explicit `timeoutSeconds` set above the 60s v1
default (120-300s depending on the function, since these all make LLM
calls).

## Required secrets / env vars

Set as Cloud Functions v2 secrets before deploying:

```bash
firebase functions:secrets:set META_MODEL_API_KEY
```

The whole backend (onboarding chat and negotiation) runs on the Meta Model
API (Muse Spark) — a single vendor, one key to manage. See CLAUDE.md Stack
decisions for why. Model API speaks the Anthropic Messages API shape
natively (confirmed from Meta's docs), so `functions/src/lib/metaModel.ts`
just points the official `@anthropic-ai/sdk` at `https://api.meta.ai` with
the Model API key as the bearer token — no custom parsing, no guesswork.

For local emulator use, copy `.env.example` to **`.env.local`** (not `.env`)
in this directory and fill in real values — `firebase emulators:start` reads
it automatically. Use `.env.local` specifically, not `.env`: Cloud Functions
v2 uploads `.env`/`.env.<project-id>` as plain environment variables on
*deploy* too, and a plain env var with the same name as a declared secret
(`META_MODEL_API_KEY`) makes the deploy fail with "Secret environment
variable overlaps non secret environment variable". `.env.local` is
emulator-only and never uploaded — see
https://firebase.google.com/docs/functions/config-env#env-variables.

**TODOs for a human before this runs end-to-end:**

- [ ] Create the actual Firebase project and replace `TODO-SET-PROJECT-ID`
      in `../.firebaserc` with its project ID.
- [ ] Set the secret above (`firebase functions:secrets:set META_MODEL_API_KEY`).
- [ ] Enable Firebase Auth anonymous sign-in in the Firebase console (Auth
      → Sign-in method → Anonymous) — this is the only auth mode assumed
      by `firestore.rules` (client uid == twinId).
- [ ] Wire up FCM device registration client-side (write the device token
      into `twins/{twinId}.fcmTokens`) so `notifyMatch` has somewhere to
      send pushes.

## Local dev

```bash
cd functions
npm install
npm run build       # tsc
npm run typecheck   # tsc --noEmit
npm run serve       # build + start functions/firestore emulators
```

## Deploy

```bash
firebase deploy --only functions,firestore:rules,firestore:indexes
```
