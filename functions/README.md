# Digital Twins backend — Firebase Cloud Functions

Firestore + Cloud Functions v2 (Cloud Run-backed) + FCM backend for the
proximity-gated digital-twin matchmaking prototype. See the repo root
`CLAUDE.md` for full product context and guardrails.

## Layout

```
functions/
  src/
    index.ts               exports every deployable function
    submitContext.ts        callable: one-shot text-dump -> profile extraction (Tier A)
    importSocialContext.ts  callable: Instagram web search / Facebook Graph API pull -> profile
    onCheckin.ts             Firestore trigger: checkins/{locationId}/people/{twinId}
    negotiateTwins.ts        twin-to-twin negotiation (callable + internal helper)
    notifyMatch.ts           Firestore trigger: matches/{matchId} -> FCM push
    types.ts                 shared Firestore document shapes
    lib/
      admin.ts               shared Firebase Admin SDK instance (db, messaging)
      secrets.ts             Cloud Functions v2 secret/param declarations
      metaModel.ts            fetch-based client for the Meta Model API
      extractProfile.ts       shared text -> {name, summary, interests} extraction call
      graphApi.ts             Facebook Graph API helper (Tier B, tester accounts only)
      parallel.ts             Parallel Search API client (Instagram web search, everyone)
```

## Two-tier context model (see root `CLAUDE.md`)

- **Tier A (every user)**: a single pasted text dump (`submitContext`), the
  existing `public_profile`-only Facebook Login for name/photo, and an
  optional Instagram handle that's fed into a public web search via the
  Parallel Search API (`importSocialContext`'s `instagram` provider — see
  `lib/parallel.ts`). No OAuth, no App Review, no Meta App Dashboard setup
  — works for any handle, at the cost of only surfacing whatever's
  actually publicly indexed about it.
- **Tier B (tester/role accounts on the Meta App only)**: real Facebook
  post text via Graph API (`importSocialContext`'s `facebook` provider),
  merged into the same profile. This only works for accounts added as a
  Tester/Developer/Admin under the Meta App's Roles panel while it's in
  Development Mode — `user_posts` is a Standard Access permission, so any
  other account's login attempt is rejected by Graph API outright, not
  slowly reviewed. `importSocialContext` treats that as a normal,
  reportable outcome (`imported: false`) rather than an error.

## Firestore data model

- `twins/{twinId}` — profile: `name`, `photoUrl`, `summary`/`interests`
  (what `negotiateTwins.ts` actually reads), `rawContext` (the user's own
  text dump), `socialContext` (Instagram web-search results for any user,
  plus Facebook post text for tester accounts), FCM tokens.
- `context_submissions/{twinId}` — audit trail of what a twin's context was
  built from (`textDump`, `socialContext`). Not read by negotiation; exists
  purely for debugging/provenance.
- `checkins/{locationId}/people/{twinId}` — presence docs; a write here is
  the (faked) proximity signal that triggers matching.
- `matches/{matchId}` — the two twin IDs, negotiation transcript, final
  plain-language reason, and status (`proposed` | `negotiating` |
  `confirmed` | `dismissed`). `matchId` is the two twin IDs sorted and
  joined with `_`.

## Functions

| Function | Trigger | Purpose |
|---|---|---|
| `submitContext` | callable | Tier A: one-shot text dump -> extracted `summary`/`interests` via the Meta Model API (Muse Spark), written to `twins/{twinId}`. |
| `importSocialContext` | callable | `provider: "instagram"` runs a public Parallel web search for the twin's own handle (works for anyone); `provider: "facebook"` pulls Facebook posts (`user_posts`) via Graph API (tester/role accounts only). Either way, re-runs extraction and merges into `twins/{twinId}`. Facebook fails gracefully (non-tester accounts); Instagram fails gracefully too (Parallel errors / no results). |
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
firebase functions:secrets:set PARALLEL_API_KEY
```

Context extraction and negotiation both run on the Meta Model API (Muse
Spark) — a single vendor, one key to manage. See CLAUDE.md Stack decisions
for why. Model API speaks the Anthropic Messages API shape natively
(confirmed from Meta's docs), so `functions/src/lib/metaModel.ts` just
points the official `@anthropic-ai/sdk` at `https://api.meta.ai` with the
Model API key as the bearer token — no custom parsing, no guesswork.

`PARALLEL_API_KEY` is a Parallel AI (https://platform.parallel.ai) Search
API key, used only by `importSocialContext.ts`'s `instagram` provider (see
`lib/parallel.ts`) to run a public web search for the twin's own Instagram
handle instead of any OAuth/Graph API flow. Entirely server-side — nothing
Instagram-related needs to be added to the Android app or the Meta App
Dashboard.

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
- [ ] Set the secrets above (`firebase functions:secrets:set ...`).
- [ ] Enable Firebase Auth anonymous sign-in in the Firebase console (Auth
      → Sign-in method → Anonymous) — this is the only auth mode assumed
      by `firestore.rules` (client uid == twinId).
- [ ] Wire up FCM device registration client-side (write the device token
      into `twins/{twinId}.fcmTokens`) so `notifyMatch` has somewhere to
      send pushes.
- [ ] For Tier B (Facebook posts only): in the Meta App Dashboard, add
      tester/demo accounts under App Roles and enable `user_posts`. See
      CLAUDE.md's two-tier context model and `android/README.md`.

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
