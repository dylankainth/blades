# Digital Twins for Human Connection — HackMIT

## Concept

Build digital twins (AI agents) of individual people, created quickly via a
lightweight text dump rather than slow OAuth scraping. Anyone can also add
an Instagram handle for a public web-search pull, and a curated set of demo
accounts can additionally connect Facebook for real post data — see "Where
the ambition breaks," item 1. Twins interact
with each other on your behalf to surface people you should meet, then
notify you when
you're physically near a good match — during an event like HackMIT with ~1000
people, or just walking around day to day.

The interaction isn't limited to a single venue/event context — the pitch is
that it transcends environments and can operate continuously, on the go.

## Challenge fit (Meta/Facebook — "Bringing People Closer Together with AI")

> Build the AI-powered social product you wish existed, or one that helps
> strengthen connections you care about in new ways. AI should play a
> meaningful role. Judged on: how meaningfully it strengthens human
> connection, how essential/well-integrated the AI is, originality, and
> strength of the working demo.

**Critical framing constraint:** the twins cannot be the point, and they
cannot be the decision-makers. If the demo reads as "I let my bot decide who
I talk to and removed myself from the loop," it reads dystopian and loses on
the "human connection" axis specifically. The twins must do the awkward,
invisible coordination labor humans avoid (surfacing shared context, saying
the honest/inconvenient thing, filtering 1000 strangers down to a few worth
meeting) — and then **hand the connection back to a human interaction**.
AI as friction-remover / facilitator, not as replacer.

Rule of thumb for every feature decision: "your twins found the plan — here
it is, confirm?" beats "your twins booked it."

## Other use cases considered (kept as inspiration / future work, not building)

- **Honest-broker group planner**: friends' twins negotiate a plan, surfacing
  what people won't say out loud ("can't afford it," "don't actually like
  hiking," "I'm burnt out"). Very demoable live, but closer to the
  challenge's own stated example — safe fit, less original.
- **Reconnection engine**: twin keeps loose tabs on dormant friends' twins,
  pings you at the right moment when there's a genuine reason to reach out.
  Original and emotionally resonant, but hard to demo live in a short window
  since the payoff is "the right moment," which is hard to fake convincingly
  on stage.
- **Caregiving coordination** (healthcare track): family members' twins
  negotiate who covers what for an aging parent; twin can say "I'm at
  capacity" when the human can't. Heavy/real but wrong energy for a live
  hackathon demo.
- **Team formation** (education track): twins match complementary skills,
  schedules, working styles for project teams / study groups.
- **Neighborhood resource web** (sustainability track): twins coordinate
  carpools/tool-sharing/surplus food. Risk: drifts into "logistics
  optimizer" and away from "human connection" — weaker fit for this
  specific challenge.

**Chosen direction for HackMIT:** the venue-scale connection-matching twin
(described above), because it's the most original, most visually demoable at
event scale, and directly usable on ourselves in the room in front of judges.

## Where the ambition breaks at hackathon scale (and the fix)

1. **Data ingestion (Gmail/Twitter/LinkedIn/GitHub/Claude history scraping)**
   — multiple live OAuth flows on stage is fragile and will break in front of
   judges. Also can feel invasive rather than magical.
   - Fix (superseded — see below): cut to at most one or two data sources,
     or skip scraping entirely. The original plan built the twin via a
     ~90-second conversational onboarding with Claude ("tell me about
     yourself, what are you hoping to get out of this weekend"). Faster to
     build, never breaks live, and feels more personal than "we read your
     LinkedIn."
   - **Current model: a text dump, not a chat interview, plus a two-tier
     social layer.** The conversational back-and-forth turned out to be
     more friction than signal — a single "paste anything about yourself"
     text box gets the same context in one round trip instead of 4-6. That
     text dump is the universal (Tier A) context source for every user,
     alongside the unchanged `public_profile`-only Facebook Login for name
     + photo. See `functions/src/submitContext.ts`.
   - **Tier A also includes Instagram, but via public web search, not
     Graph API.** Instagram's own API needs a Professional (Business/
     Creator) account plus a tester role on the Meta App — a non-starter
     for most real users. Instead, the user just types their own handle,
     and `importSocialContext.ts` runs a public web search for it via the
     Parallel Search API (`functions/src/lib/parallel.ts`), folding
     whatever's publicly indexed (bio mentions, post excerpts, press/blog
     mentions) into the same extraction pipeline as the text dump. No
     OAuth, no App Review, no Meta App Dashboard setup, works for any
     handle — at the cost of only surfacing what a search engine has
     actually indexed about it, rather than a guaranteed pull of the
     user's own recent posts.
   - **Tier B — real Facebook Graph API post scraping, tester accounts
     only.** Meta's Development Mode lets any account with a role
     (Admin/Developer/Tester) on the Meta App grant almost any permission
     — including `user_posts` — with zero App Review, because Standard
     Access permissions are usable immediately by role users. The catch:
     this is a hard platform boundary, not a review-speed problem — it
     works *only* for a short, manually-curated list of accounts (you +
     specific demo accounts, added under App Roles), never for a random
     attendee. Real attendees always fall back to Tier A. See
     `functions/src/importSocialContext.ts` and the "Connect Facebook
     posts" button in `OnboardingScreen.kt`.
   - The original `public_profile`/`email`-only reasoning still holds for
     Facebook: since 2018, Graph API gates anything beyond those two behind
     Meta's App Review process for non-role users, which takes days to
     weeks (and, for `user_posts` specifically, has an allowed-usage policy
     — memory books, parental monitoring — that a matchmaking app doesn't
     obviously fit anyway). Facebook Login for the general population stays
     scoped to name/photo only; it is not a crawl.

2. **1000×1000 pairwise agent negotiation** — combinatorially this is ~500k
   conversations, not feasible or necessary.
   - Fix: don't precompute a global shortlist at all. Gate matching by
     proximity itself — when someone checks in at a location, only run
     LLM-to-LLM negotiation against the small set of people currently/
     recently checked in there (tens, not thousands). No embedding index,
     no background batch job; compute scales with actual foot traffic, not
     the full attendee pool. Trade-off: you only ever surface matches among
     people who actually cross paths at a check-in point — you may miss a
     great match who never co-locates, but that's an acceptable (arguably
     more honest — "worth talking to right now, near you") trade for a
     hackathon build. Watch latency: negotiation now runs live between
     check-in and notification, so it needs to be fast enough that the
     person is plausibly still there.

3. **True background proximity detection (BLE/phone background location) as
   you "walk past someone"** — real background Bluetooth proximity on
   iOS/web is not a weekend build in general; OS permission plumbing alone
   eats the available time, and web (PWA) background BLE/geolocation on
   Android is too unreliable (service workers get suspended, no real
   background Web Bluetooth scanning) to demo live.
   - Scope decision: build it for real, Android-only, as a native app
     (Kotlin, not Flutter — no cross-platform need since iOS is out of
     scope, and talking to Android's BLE/foreground-service/background-
     location APIs directly avoids an extra plugin-abstraction layer that
     could misbehave under time pressure). A foreground/background service
     advertises + scans BLE to detect nearby twins and feeds real proximity
     events into the matching engine from item 2. This replaces the earlier
     "fake the sensor, use QR check-in" fallback as the primary plan. A
     manual QR/check-in backup was scaffolded initially but removed once
     real BLE proximity was confirmed working end-to-end on real hardware
     (two phones, live detection → negotiation → match) — BLE is now the
     sole proximity trigger, no manual UI fallback exists.

## Stack decisions

- **Android app: Kotlin + Jetpack Compose, native — not Flutter, not Expo/RN.**
  Reason is specific to BLE, not general dev speed: BLE *central* (scanning)
  is well-supported by cross-platform libraries, but BLE *peripheral*
  (advertising) — which this app needs, since every phone must advertise
  and scan simultaneously — is thin and poorly maintained in both the RN
  and Flutter ecosystems. Native gives direct control over the one
  component the whole demo hinges on, instead of debugging someone else's
  plugin internals through a bridge under time pressure. Also want direct
  manifest control for Android 14's foreground-service-type declarations.
  No cross-platform cost paid since there's no iOS target.
- **BLE approach: broadcast-only, no GATT connections.** Each phone
  advertises its twin ID in the BLE advertisement payload (manufacturer/
  service data); other phones detect it via scanning. No pairing or
  connection handshake needed — this is the same pattern contact-tracing
  and proximity apps use, and it's far more reliable at a crowded venue
  than trying to form BLE connections between many phones.
- **Permissions**: on Android 12+, request `BLUETOOTH_SCAN` with the
  `neverForLocation` flag so background BLE scanning doesn't also require
  location permission — avoids an extra permission prompt live on stage.
- **Foreground service, not "app must be open."** A foreground service
  keeps BLE advertise/scan running while the app is backgrounded or the
  screen is off — it does not require the UI to be visible. Trade-offs to
  handle explicitly: (1) Android requires a persistent, unremovable
  notification while it runs — plan for it rather than fight it; (2) OEM
  battery killers (Samsung/Xiaomi/etc.) can still kill foreground services
  regardless of stock Android rules — prompt the user to disable battery
  optimization for the app; (3) swiping the app away from Recents can kill
  the service depending on `onTaskRemoved` handling — test this specific
  case, since it's a natural thing to do while walking around an event.
- **Backend: Firebase** (Firestore + Cloud Functions + FCM) as a single
  vendor, to avoid gluing together separate DB/functions/push infra under
  time pressure.
- **Web search: Parallel AI Search API** (`https://api.parallel.ai/v1/search`,
  plain `x-api-key` auth) for the Instagram side of Tier A context — see
  "Where the ambition breaks," item 1, and `functions/src/lib/parallel.ts`.
  Chosen over standing up the `parallel-cli` tool (which needs an
  interactive device-code browser login) since the backend only needs a
  single stateless REST call per import, keyed with the API key as a
  Cloud Functions secret (`PARALLEL_API_KEY`) exactly like
  `META_MODEL_API_KEY`.
- **Models: Meta Muse Spark for both context extraction and
  twin-to-twin negotiation** (via the self-serve Meta Model API —
  developer.meta.com, $20 free credit, then pay-as-you-go). Originally
  split (Claude for onboarding, Muse for negotiation); consolidated onto
  Muse Spark alone to cut down on API keys/secrets to manage under time
  pressure and lean further into the Meta track story. Muse Spark is
  purpose-built and marketed for multi-agent orchestration/tool-calling,
  which is a direct, literal match for the negotiation step in particular
  — using Meta's own agent-orchestration model for the actual
  agent-to-agent negotiation is the strongest "essential and
  well-integrated AI" story for the Meta/Facebook track. Confirmed from
  Meta's docs: Model API speaks the Anthropic Messages API shape natively,
  so both call sites go through a shared client
  (`functions/src/lib/metaModel.ts`) that points the official
  `@anthropic-ai/sdk` at `https://api.meta.ai` (model `muse-spark-1.3`)
  with the Model API key as the bearer token — no custom parsing needed.
- **Cloud Functions architecture**: no long-lived session/connection needed
  anywhere. Context submission is now a single stateless request/response
  call (`submitContext` — the text dump in, a structured profile out; see
  item 1 above for why this replaced multi-turn onboarding chat), and
  negotiation remains its own sequence of short calls. Use **Cloud
  Functions 2nd gen** (Cloud Run-backed, up to 60 min timeout) over 1st gen
  (60s default) for headroom, not because any single call should take
  anywhere near that long. The one part worth sanity-checking once running:
  if twin negotiation involves several agentic back-and-forth turns per
  pair, time it — should still land in seconds, but confirm rather than
  assume.
- **Judge-facing negotiation view: a lightweight web page** (plain JS or
  React) subscribed live to a Firestore collection — not a second mobile
  screen — so it can run on a laptop while presenting.

## What to actually build, in priority order

1. **Fast twin creation** — a single text-dump box ("paste anything about
   yourself"), plus an optional Instagram handle (public web search, works
   for anyone) and an optional Facebook connect button for the curated
   tester-account tier (see "Where the ambition breaks," item 1). This is
   the opening beat of the demo.
2. **Matching engine with a visible "why"** — not a score/percentage. The
   twin should explain its reasoning in plain language, e.g. "you're both
   stuck on the same devops problem" / "she's looking for a co-founder with
   your background." The reasoning is the product.
3. **The notification moment** — the centerpiece of the interface. Tone as a
   message from *your own agent*, not a corporate match alert. Should feel
   personal/warm, not like a dating-app percentage match.
4. **Visible agent-to-agent negotiation screen** (for presenting to judges
   specifically) — show two twins' actual exchange on screen, not just the
   final output. This is the "wow" moment: visible negotiation collapsing
   into a human-readable outcome.
5. **Proximity trigger** — native Android app (Kotlin) doing real BLE
   background advertise/scan to detect nearby twins; a detected proximity
   event triggers live pairwise negotiation against whoever's actually
   nearby, not a lookup against a precomputed global shortlist. Confirmed
   working live on real hardware — no manual/QR fallback needed or built.

## Interface notes (priority: interface is the product)

- **Pages/screens built** (all in the one native Android app except the
  last):
  1. Welcome — app-opening screen, gradient hero with the animated
     character, leads into Sign In / Sign Up.
  2. Onboarding — the text-dump twin-creation screen, plus an optional
     Instagram handle field (public web search, works for anyone) and an
     optional "Connect Facebook posts" button (tester accounts only — see
     "Where the ambition breaks," item 1).
  3. Home — idle/listening state (animated character, fully invisible BLE
     proximity underneath, no manual check-in UI) plus a live feed of
     recent negotiations.
  4. Notification screen — the centerpiece; see tone notes below.
  5. Match/handoff screen — what tapping a confirmed match opens into.
  6. Negotiation detail screen — for a non-match: full transcript + score
     + reason, opened by tapping a dismissed item in the Home feed.
  7. Judge-facing negotiation view — separate lightweight web page (not
     in the Android app), live-subscribed to Firestore.
- Mock the phone notification screen first, before backend work — get the
  wording, tone, and visuals nailed down early since it's the single screen
  that carries the whole pitch.
- Notification should include: photo/name of the person, one specific
  concrete reason to talk to them (not a score), and a single tap action
  that hands off to a real human interaction ("say hi") — never an
  auto-generated intro message sent on your behalf, and never an
  auto-scheduled/auto-booked meeting.
- Example tone: "Sarah's twin and I think you two should talk — she's stuck
  on the same devops problem you solved" — specific and human, not "87%
  match."
- Keep a separate judge-facing screen/view that shows the twin negotiation
  transcript live — this is not for end users, it's to prove the AI is
  doing real, essential, legible work (judging criterion: "how essential and
  well-integrated the AI is").

## Guardrails to repeat in the pitch itself (say this out loud to judges)

- Twins surface and suggest; they do not decide, message, or book on a
  human's behalf.
- Every output ends in a human-in-the-loop confirmation step, never an
  autonomous action taken against another person.
- This is explicitly framed to judges as *why* the design avoids the
  dystopian "bots deciding who you talk to" read — state the reasoning, don't
  leave it implicit.
