# Kindred — End-to-End Execution Plan

Companion to [`TRACKS.md`](TRACKS.md) (why each track) and [`CLAUDE.md`](CLAUDE.md) (why the
product is shaped this way). This file is the *what to actually do, in what order*.

**Assumptions — correct these if wrong, they change the cut lines:**

- Submission deadline is Sunday morning. Phases below are relative hours (`H+0` = now), not
  clock times — map them onto your real deadline.
- Team of ~4. Lanes below are roles, not people; with fewer people, work top-to-bottom
  within each lane in the order given.
- BLE phone-to-phone (`android/app/src/main/java/com/hackmit/twins/ble/`) is the single
  riskiest item and is **not** finished. Everything here is ordered around protecting it.

---

## Part 1 — Final track list

**Locked (7).** These are the portfolio. Seven submissions, all honest.

| # | Track | What qualifies us | Owner lane |
|---|---|---|---|
| 1 | **Meta** | The whole product | All |
| 2 | **The Token Company** | Cost instrumentation + measured before/after | Backend |
| 3 | **Ramp** | Slide — time saved, with a real number | Pitch |
| 4 | **Long Lake** | Slide — the guardrails *are* the skeptic's answer | Pitch |
| 5 | **Cognition (Devin)** | Build process, no product change | All |
| 6 | **Deepgram** | STT on onboarding | Backend + Android |
| 7 | **ElevenLabs** | TTS twin voices on the judge dashboard | Backend + Frontend |

**Hardware-unlocked (2).** New since `TRACKS.md` — see Part 2 for the reasoning.

| # | Track | What qualifies us |
|---|---|---|
| 8 | **Hackster (Nordic)** | nRF52840-DK as the venue beacon |
| 9 | **Espressif** | ESP32-S3 as additional beacons (AIoT) |

**Free but thin (1).** Enter it, don't build for it, don't expect to place.

| # | Track | What qualifies us |
|---|---|---|
| 10 | **ASUS** | ZenScreen portable monitor as the dedicated judge-dashboard display |

ASUS's challenge is literally *"use ASUS products to build your projects."* You need a second
screen for the judge view anyway (`CLAUDE.md` specifies it runs on a laptop while
presenting). Using their monitor for it is honest — it's just thin, and judges will rank
teams who built *for* ASUS hardware above you. Free entry, no effort, no slide.

**Stretch (2), only if everything above is done:** Elastic, GiveCampus. See `TRACKS.md`.

**Still declined:** OpenAI, Warp, Dropbox, SpaceXAI, Visa, Maximor, Voloridge, Arrowstreet,
Regeneron, Arduino, Dimensional, Linq. Reasons in `TRACKS.md`.

---

## Part 2 — Hardware

### Should you add hardware at all?

Yes — but for one engineering reason first, and the prizes second. If the reasoning below
doesn't hold up on the day, drop it; don't let a prize drag the architecture around.

**The real argument: your riskiest component is Android BLE *peripheral* advertising.**

`CLAUDE.md` already identifies this — every phone must advertise *and* scan simultaneously,
and peripheral advertising is the thin, flaky half. A fixed beacon changes the demo's
dependency: phones only need to **scan** (central role, well supported, reliable) to detect
"someone is at this location." That's a strictly easier problem than two phones discovering
each other across a noisy room.

Three concrete wins:

1. **A known-good reference signal.** When BLE isn't working at 2am, a beacon you control
   tells you instantly whether the problem is your scan code or your advertise code. Right
   now you have no way to isolate that.
2. **The nRF52840-DK doubles as a BLE sniffer.** Flash Nordic's sniffer firmware, open
   Wireshark, and you can see whether your phone's advertisements are *actually going out*.
   This is the best debugging tool that exists for the exact failure you're most likely to
   hit, and it's sitting on the shelf. **This alone justifies the checkout slot.**
3. **A visible demo prop.** "These two phones saw each other, trust me" is a weak stage
   moment. A physical thing on the table that the demo visibly reacts to is a strong one.

### ⚠️ The tension you must not ignore

`CLAUDE.md` pitches Kindred as *"not limited to a single venue/event context — it transcends
environments and can operate continuously, on the go."* Beacons are venue infrastructure.
Lean on them too hard and you've quietly become a venue product, which undercuts the pitch.

**Resolution — say this explicitly to judges:** phone-to-phone BLE stays the primary
mechanism and is what makes it work anywhere. The beacon is a *venue amplifier* for dense
events, and a fallback. Do not remove phone-to-phone from the demo. If you find yourself
demoing only beacons, you've drifted — stop and fix the phone path instead.

### The cart

Checkout rules: **up to 5 different parts per request; once marked Ready you have 5 minutes
to collect or it goes back on the shelf.** Only send when someone can walk over immediately.

**Request 1 — send now. Ordered by scarcity, not importance.**

| Part | Stock | Why |
|---|---|---|
| **NRF52840-DK** | **2 / 2** | Beacon + BLE sniffer. Unlocks Hackster. Request both units. |
| **Anker Power Bank 10000mAh 30W** | 3 / 8 | BLE advertise+scan on a foreground service will eat a phone battery in hours. Least glamorous, possibly most important item here. |
| **ASUS ZenScreen MB169CK-P** | 11 / 30 | Judge dashboard display. Unlocks ASUS. |
| **Espressif ESP32-S3-DevKitC** | 164 / 200 | Additional beacons. Unlocks Espressif. |
| **Arduino USB-C Cable** | 23 / 46 | Powers/flashes the ESP32-S3. |

The nRF52840-DK is **2 of 2 remaining** — if you do one thing after reading this, send that
request.

> ⚠️ **Cable gotcha:** the nRF52840-DK is micro-USB (verify at the booth). Every micro-USB
> cable in the inventory reads **0 available** — Amazon Basics 3-pack, the bulk 12-pack, the
> BRENDAZ. Ask at the booth or borrow one from another team *before* you're depending on it.

**Do not check out:** Arduino UNO Q (a third platform and a different toolchain — App Lab,
Linux — for a "Touch Grass" fit that's a stretch anyway), cameras, heart-rate sensors,
motors. None of them serve this product.

### What to build with it

| Board | Job | Effort | Unlocks |
|---|---|---|---|
| nRF52840-DK #1 | BLE sniffer, Wireshark, debugging only | ~30 min setup | — (internal) |
| nRF52840-DK #2 | Venue beacon — advertises the Kindred service UUID | ~1–2 h | Hackster |
| ESP32-S3-DevKitC | Additional beacons, same UUID scheme | ~1 h after the first | Espressif |

Integration is small because the scaffolding exists: the beacon advertises the service UUID
already defined in `android/.../ble/BleConstants.kt`; `BleProximityService.kt` scans and
recognises it; a hit calls the same path `CheckinRepository.kt` already uses, landing in
`functions/src/onCheckin.ts`, which already triggers negotiation. **No new backend.**

Keep the beacons headless. Do not add displays or LEDs — the phone notification is the
centerpiece (`CLAUDE.md`), and a blinking box on the table competes with it.

---

## Part 3 — Credits: claim in the first hour

Do this **before** any building. Some are capped, and approvals aren't instant.

| Sponsor | What | How | Blocking? |
|---|---|---|---|
| **Meta** | $50 Model API | dev.meta.ai account + intake form | **Yes — nothing runs without it** |
| **Deepgram** | $200, no card | <https://dpgr.am/hackmit26> — auto-applied at signup | **Yes, for voice** |
| **ElevenLabs** | Free tier only | No HackMIT credit listed — **ask at their booth** | Yes, for twin voices |
| **Cognition** | $1,000 Devin | Form in the credits doc, pick up at booth | No, but do it now |
| **The Token Company** | Compression models | thetokencompany.com | No |
| **SpaceXAI** | 1 mo Cursor Pro | Booth visit | No — free dev tooling |
| **Mintlify** | 1 mo Pro | Code `MINTHACKMIT` | No — optional docs polish |

**Caps:** OpenAI 320 participants, Notability 500 redemptions, RunPod 250 codes. Meta and
Deepgram look uncapped — claim anyway.

**Not applicable:** RunPod, Fragment, Voloridge, Regeneron, Notability, Elastic (unless you
take the stretch), Warp, OpenAI.

---

## Part 4 — Build plan

### Lane A — Android (highest risk, protect it)

`android/app/src/main/java/com/hackmit/twins/`

1. **`H+0 → H+4` · Make BLE actually work.** Nothing else in this lane matters until
   `BleProximityService.kt` reliably advertises and scans between two real phones. Test the
   three failure modes `CLAUDE.md` already names: OEM battery killers, `onTaskRemoved` when
   the app is swiped from Recents, and the Android 12+ `BLUETOOTH_SCAN` /
   `neverForLocation` permission path.
2. **`H+4 → H+5` · Beacon detection.** Recognise the beacon UUID in the existing scan
   callback, route to the existing check-in path. Small, because `CheckinRepository.kt`
   already exists.
3. **`H+5 → H+8` · Voice onboarding UI.** Record in `OnboardingScreen.kt`, upload, play the
   response. Relabel the existing typing indicator as listening/thinking.

### Lane B — Backend

`functions/src/`

1. **`H+0 → H+2` · Cost instrumentation.** → Token Company + Meta.
   - `lib/metaModel.ts` currently **discards `response.usage` entirely** — `callMetaModel`
     returns only the text. Change it to return usage alongside the text. That's the hook
     everything else hangs off.
   - Write per-call token counts to Firestore from `negotiateTwins.ts` and
     `onboardingChat.ts`.
   - **Free win already documented in your own code:** the comment in `metaModel.ts` records
     Muse Spark burning **1021 of 1024 tokens on thinking with empty visible output**, which
     is why `maxTokens` defaults to 4096. That is a measured, real inefficiency in your own
     stack. Tune it, record the before/after, and you have a genuine optimization story
     rather than a theoretical one. Most Token Company entries won't have a measured number.
2. **`H+2 → H+5` · Deepgram STT.** New `lib/deepgram.ts`; audio endpoint feeding
   `onboardingChat.ts`. Batch, not streaming. **Keep Muse Spark as the brain — do not use
   Deepgram's end-to-end Voice Agent API.**
3. **`H+5 → H+6` · ElevenLabs TTS.** New `lib/elevenlabs.ts`; render each negotiation turn in
   `negotiateTwins.ts` with a per-twin voice, store the URL on the turn.
4. **`H+6 → H+7` · Verify `cache_control`.** Does prompt caching work against `api.meta.ai`?
   The Messages API *shape* does not guarantee it. If yes, cache the twin profile prefix. If
   no, drop it — the structural saving is the bigger number anyway.

### Lane C — Judge dashboard

`judge-dashboard/index.html` (426 lines, single file — keep it that way)

1. **`H+2 → H+3` · Live cost counter.** → Token Company + Ramp.
   *"Naive all-pairs for 1,000 attendees: ~500,000 negotiations, ~$X. Kindred tonight: N
   negotiations, $Y."*
2. **`H+6 → H+8` · Spoken negotiation.** Play each turn in its twin's voice as it streams
   in. **Pre-render a fallback audio file** — do not let a live API call be the thing that
   breaks on stage.

### Lane D — Hardware & pitch

1. **`H+0` · Send the cart request.** Someone walks over immediately.
2. **`H+0 → H+1` · Claim every credit in Part 3.**
3. **`H+1 → H+2` · nRF sniffer up.** Hand it to Lane A — it is a debugging tool for them.
4. **`H+2 → H+4` · Beacon firmware**, nRF first, then ESP32-S3.
5. **`H+4 → H+5` · Three slides.** Time-saved number (Ramp), skeptic answer (Long Lake),
   guardrails (Meta). Pull the guardrails text verbatim from `CLAUDE.md` — it's already
   written.
6. **`H+8 → H+10` · Demo video + write-up.** See below.

### ⛔ Hard-scheduled: the Meta deliverables

Meta requires **a working prototype, a 2–3 minute demo video, a public code repo, and a
write-up** covering who it's for, how it strengthens connection, and why AI is essential.

**Start the video at `H+8`, whatever state the build is in.** This is the single most common
way good hackathon projects lose: a great demo, no video, submitted at 8:59am. The video is
the artifact judges actually review, and it's the only deliverable whose quality doesn't
depend on the venue's RF conditions.

---

## Part 5 — Cut lines

Cut from the bottom.

| Hours left | Do this |
|---|---|
| **< 20 h** | Drop Elastic and GiveCampus. Locked 7 + hardware 2. |
| **< 12 h** | Drop ElevenLabs and the ESP32 beacons. Keeps Meta, Token Co, Ramp, Long Lake, Devin, Deepgram, Hackster, ASUS. |
| **< 8 h** | Drop Deepgram voice. Do cost instrumentation, the counter, the slides, the video. That's ~4 h and still adds Token Co, Ramp and Long Lake while making Meta stronger. |
| **BLE unstable at any point** | Stop everything else. A working proximity trigger is worth more than a talking one — item 5 in `CLAUDE.md` is the part of the demo that can't be faked. |

**First thing to cut is always voice. Last thing to cut is always BLE.**

---

## Part 6 — Risks

| Risk | Mitigation |
|---|---|
| BLE peripheral advertising unreliable in a crowded hall | Beacons as fallback; nRF sniffer to diagnose; QR check-in still wired up |
| Micro-USB cable for nRF52840-DK is out of stock everywhere | Ask at the booth / borrow **before** you depend on it |
| Phone battery dies mid-demo | Anker power bank in the cart — actually use it |
| Live TTS call fails on stage | Pre-rendered fallback audio |
| ElevenLabs free-tier quota exhausted | Confirm headroom at their booth before building on it |
| Voice adds seconds to an already multi-turn negotiation | Time end-to-end once, early — `CLAUDE.md` already flags this |
| Beacons drift the pitch into "venue product" | Keep phone-to-phone in the demo; say the framing out loud |
| Video left until the last hour | Hard-scheduled at `H+8` |

---

## Part 7 — Submission checklist

| Track | Needs |
|---|---|
| **Meta** | Prototype, 2–3 min video, public repo, write-up |
| **Token Company** | Before/after cost numbers + the dashboard counter |
| **Ramp** | Slide with the time-saved number |
| **Long Lake** | Slide — the skeptic's objection and your answer |
| **Cognition** | Note what Devin actually built; credits form submitted |
| **Deepgram** | Must call a Deepgram API — STT on onboarding |
| **ElevenLabs** | Show agentic depth — the live twin-to-twin exchange, voiced |
| **Hackster** | Working Nordic-board prototype (the beacon) |
| **Espressif** | Espressif hardware in an AIoT context (the beacons) |
| **ASUS** | ASUS hardware used in the project (the ZenScreen) |

**Repo is public before you submit anything** — Meta and Regeneron both require it, and it's
easy to forget while the repo is still private.

---

## Right now, in order

1. Send the hardware request (nRF52840-DK is 2 of 2 left).
2. Claim Meta + Deepgram credits.
3. Lane A starts on BLE. Lane B starts on `lib/metaModel.ts` returning usage.
