# Kindred — Sponsor Track & Credits Strategy

**Primary track: Meta — "Bringing People Closer Together with AI."**

Every other track on this page is judged against one question:

> Does this make the Meta demo **better**, **worse**, or **neutral**?

Nothing that makes it worse goes in. The Meta Round 2 invite (travel + accommodation to
Menlo Park) is the largest prize on the board and the only one that isn't hardware or a
one-off cash payout. Protect it.

The good news: the three highest-value additions below (voice, cost instrumentation,
time-saved framing) all make the Meta demo *better*, not merely parallel. That isn't a
coincidence — they're the parts of the pitch that are currently told rather than shown.

> **Submission cap: none.** Confirmed with organizers — a project may enter as many sponsor
> challenges as it likes. So entering a track costs you nothing but the work it takes to
> qualify honestly. The binding constraint is **hours and demo quality**, not slots: every
> track below is priced in effort, and the declines are quality judgments rather than
> rationing. If a track needs zero code, there is now no argument against entering it.

---

## TL;DR — recommended portfolio

| Track | Fit | Product change needed | Effort | Prize |
|---|---|---|---|---|
| **Meta** | Native | — | — | Round 2 @ Menlo Park + swag |
| **Deepgram** | Strong | Voice onboarding | ~3–4 h | Nintendo Switch / member |
| **ElevenLabs** | Strong | Twin voices in negotiation | ~2 h | Not stated |
| **The Token Company** | Strong | Instrumentation only | ~1–2 h | $500 + SF interview route |
| **Ramp** | Framing | None | ~0 h | Switch + Oura + $100/member |
| **Long Lake** | Framing | None | ~0 h | Top 3 receive prizes |
| **Cognition (Devin)** | Process | None | ~0 h | $5K to one team |
| **Elastic** | Stretch | Twin memory layer | ~4–6 h | Quest 3S / member |

> **Superseded in part by [`PLAN.md`](PLAN.md).** After reviewing the hardware checkout
> inventory, two more tracks became reachable — **Hackster** (nRF52840-DK as a venue beacon)
> and **Espressif** (ESP32-S3 beacons) — plus **ASUS** as a free-but-thin entry. This page
> is still the *why* for each track; `PLAN.md` is the execution order, hardware cart, credit
> claims and cut lines.

Everything else: declined, with reasons in the last section. With no submission cap the only
reason to decline is quality — a track you can't qualify for honestly still costs you the
hours you spend failing to, and a visibly bolted-on entry is a bad look in front of a sponsor
you may want to interview with later.

---

## Tier 1 — take these

### 1. Meta — primary

Already the design target; `CLAUDE.md` is written against this rubric. No changes.

One thing to check against the official challenge text: the deliverables are **a working
prototype, a 2–3 minute demo video, a public code repo, and a short write-up** covering who
it's for, how it strengthens connection, and why AI is essential. The video and write-up
are not optional and are easy to leave until 3am. Budget an hour.

The guardrails section of `CLAUDE.md` (twins surface, never decide/message/book) is the
strongest thing about this submission. Say it out loud to judges — don't let it stay
implicit in the code.

### 2. Deepgram — "Build Something Worth Talking To"

**Why it fits honestly:** the onboarding is already specified as a ~90-second
*conversational* flow. Right now "conversational" means typing, which is the weakest
possible reading of the word. Making it literally spoken is the single change that most
improves the Meta demo — watching someone talk their twin into existence in 90 seconds on
stage is a completely different beat from watching them type.

**Qualification bar:** "your project must call a Deepgram API." Speech-to-text on the
onboarding turn clears it explicitly.

**Architecture — fits the existing stateless design exactly:**

```
Android records turn -> uploads audio to Cloud Function
  -> Deepgram STT (batch, not streaming - simpler, fast enough)
  -> append transcript to Firestore
  -> Muse Spark generates next question
  -> TTS -> return audio URL
  -> Android plays it
```

No long-lived connection, no change to the "each turn reads the transcript from Firestore"
model in `CLAUDE.md`. Expect ~2–4 s per turn; the typing-indicator decision already made
covers it — just relabel it a listening/thinking indicator.

**Do NOT use Deepgram's end-to-end Voice Agent API.** It would replace Muse Spark as the
brain and gut the Meta story. Deepgram is ears and mouth; Muse Spark stays the brain. That
split is also the honest answer if a Deepgram judge asks why you skipped their agent product.

**Credits:** $200/hacker, no credit card, applied automatically at signup —
<https://dpgr.am/hackmit26>. Easiest credit on the board; grab it even if you cut the feature.

### 3. ElevenLabs

**Why it fits honestly:** their stated criteria are *agentic depth* ("autonomous agents
handling complex logic and real-time dialogue"), *interaction design*, and *technical
integration*, especially multimodal. Item 4 of the build priority list — the judge-facing
screen showing two twins actually negotiating — **is** two autonomous agents in real-time
dialogue. It's currently silent text. Give the two twins distinct voices and play the
negotiation aloud on the judge dashboard.

Cheapest "wow" on the list: the negotiation logic already exists, so you're adding a TTS
call and an audio element to a page you're already building.

**Division of labour with Deepgram**, so neither submission looks like padding:

- Deepgram → STT, human ↔ twin, onboarding. "Transcribing speech to text." ✓
- ElevenLabs → TTS, twin ↔ twin, the negotiation theatre. Distinct voice per twin. ✓

**Caveat:** ElevenLabs appears in the challenges doc but **not** in the sponsor credits doc —
assume free-tier quota only. Check at their booth before building a demo that depends on
volume, and pre-render a fallback audio file.

### 4. The Token Company — LLM cost saving

**The most underrated track on the board for this specific project, and it needs almost no
new code.**

You already made the cost-optimization decision — you just haven't measured it. Item 2 of
`CLAUDE.md` rejects 1000×1000 pairwise negotiation (~500k LLM conversations) in favour of
gating by proximity, so you only ever negotiate against the tens of people actually
co-located. That's a genuine, structural, quantifiable LLM-cost win, arrived at for
engineering reasons rather than retrofitted for a prize. That provenance is worth saying
out loud — it's what separates you from teams bolting on a caching layer on Sunday.

**What to build (~1–2 h):**

1. Log token counts and cost per negotiation to Firestore.
2. Put a live counter on the judge dashboard: *"Naive all-pairs for 1,000 attendees:
   ~500,000 negotiations, ~$X. Kindred actual tonight: N negotiations, $Y."*
3. Secondary win worth mentioning: a twin's own profile is a stable prefix across every
   negotiation it runs — a natural prompt-caching target.
   ⚠️ **Verify before claiming.** `CLAUDE.md` notes Meta's Model API speaks the Anthropic
   Messages API shape, but that does *not* guarantee `cache_control` is supported. Test it.
   If it isn't, the structural argument stands alone and is the bigger number anyway.

That counter doubles as a Meta asset: concrete evidence the AI is doing real, bounded,
engineered work rather than being sprinkled on.

**Prize:** $500 cash + boosted interview route in SF. Sign-in unlocked Fri the 18th at
thetokencompany.com.

### 5. Ramp — "Save Time. Save Money."

Deliberately the broadest challenge on the board: *"Build anything that saves people time
and money."* **Zero product change — this is a slide.**

The honest framing: finding the three people at a 1,000-person event genuinely worth your
time currently costs you a weekend of random hallway conversations, and you still miss most
of them. Kindred spends ~90 seconds of your time and a few cents of inference. Pair it with
the Token Company counter and you have an actual number instead of a vibe.

### 6. Long Lake — "Convince a Non-Believer"

*"Imagine an AI-powered product a skeptic would try, love, and want to use again."*
**Zero product change — also a slide, and a strong one.**

The AI skeptic's objection to AI social products is precisely *"bots talking to bots,
parasocial slop, I don't want a robot managing my friendships."* The guardrails section of
`CLAUDE.md` was written as an answer to exactly that objection — twins do the awkward,
invisible coordination labour humans avoid, then hand the connection back to a human.

You're in the unusual position of having a core design document that is already an argument
against the skeptic. Most teams entering this track will be inventing that story on Sunday
morning. Reuse the guardrails slide verbatim.

### 7. Cognition — "Best Use of Devin"

**A process prize with no product constraint.** $1,000 in Devin credits, $5K to the winning
team, judged on creativity, novelty and polish of what you built *with* Devin. Nothing about
Kindred has to change.

Practical use given the time budget: point Devin overnight at work that's parallelizable and
well-specified but tedious — the judge dashboard, Firestore security rules, instrumentation
plumbing, BLE service tests — while the team sleeps or works on the demo. Credits form is
linked in the credits doc; pickup is at their booth.

---

## Tier 2 — only if you're ahead of schedule

### Elastic — "Find the Signal"

**The honest fit, and the tension.** `CLAUDE.md` item 2 explicitly rejects an embedding index
and a precomputed global shortlist. Do **not** walk that back — it's a good decision, and
reversing it for a prize is exactly the kind of scope creep that kills hackathon demos.

The use that doesn't contradict it: Elasticsearch as the **twin's memory**, not as a global
matcher. Index onboarding transcripts, past negotiation outcomes, and stated interests as
documents. Before negotiating with a nearby stranger, a twin queries its own memory for
*"what do I know about my human that's relevant to this person?"* and ranks the tens of
co-located candidates. Retrieval-augmented negotiation, scoped to people already physically
nearby. The "Find the Signal" narrative writes itself: 1,000 people of noise in a room, one
specific reason two of them should talk.

**Effort ~4–6 h**, plus a new external dependency and a new failure point on demo day. Put it
behind a flag with a Firestore fallback, or skip it. 30-day Elastic Cloud trial, self-serve,
no booth visit required.

---

## Declined — and why

Worth knowing the reasons, so you can answer when a mentor pushes one of these at you.

| Track | Why not |
|---|---|
| **OpenAI (5th Teammate)** | Requires the OpenAI API to visibly power the experience. Either it goes in the core loop — undercutting the Muse Spark story that is your strongest Meta asset — or it stays peripheral and scores badly on OpenAI's own rubric. You'd half-ass two submissions. It's also a second model key, which `CLAUDE.md` consolidated away from on purpose. Credits go only to challenge submitters, so there's no free lunch to grab either. |
| **Warp (Best Developer Tool)** | Kindred is not a developer tool. No amount of framing fixes that. |
| **Dropbox** | The *StudentOS* bullet is genuinely adjacent — connecting people to what they're working on. But Dropbox's rubric is anchored on files and content chaos, and Kindred deliberately doesn't ingest documents (`CLAUDE.md` item 1 rejects OAuth scraping as fragile and invasive). Fitting this means building the thing you already decided not to build. |
| **SpaceXAI** | Hard requirement: real space data plus the Grok Imagine or Voice API. Wrong domain. |
| **GiveCampus** | The closest of the declines, and worth re-reading if you finish early — see the note below the table. |
| **Visa, Maximor, Voloridge, Arrowstreet, Regeneron** | Wrong domains — commerce/payments, CFO finance workflows, public-dataset analysis, greenwashing detection, clinical trials. Each expects domain work you have no reason to do. |
| **Arduino, Espressif, Dimensional, ASUS, Hackster** | Hardware tracks. There's a superficial BLE adjacency (an ESP32 as a booth check-in beacon for the fallback trigger), but Espressif's challenge is about their Private Agents platform rather than the chip, and the rest need real hardware builds. ASUS's "use our products" is thin enough that judges will see through it. |
| **Linq (iMessage/RCS/SMS API)** | Not a judged challenge in these docs, and the obvious use — auto-sending the intro message — **directly violates your own guardrail**: "never an auto-generated intro message sent on your behalf." Using it would weaken the Meta pitch. |

### The GiveCampus near-miss

Flagging this one properly because the rubric alignment is genuinely striking, and with no
submission cap it's the only decline that could flip.

Their ask is *"help a fundraising team figure out who to reach out to, and why"* — including
*"a copilot that answers: who are the 20 people I need to reach before Giving Day, and why
those 20?"* And their judging note is **"we're looking for judgment about the job, not just
accuracy on a metric."**

That is, almost word for word, your item 2: *matching with a visible "why" — not a score or
a percentage. The reasoning is the product.* You would be entering with a thesis you already
hold rather than one invented for the occasion.

**Why it's still a decline for now:** it needs a real port — pointing your reasoning engine
at their constituent CSV, in a domain (alumni fundraising) with none of your proximity or
twin-to-twin machinery. Call it 3–4 h for something credible, and it shares no code path with
the demo you're actually presenting. The prizes are also the softest on the board (lunch with
leaders, a mentorship session, a $500 donation to a nonprofit) — real career value if you want
GiveCampus specifically, low material value otherwise.

**Revisit it if and only if** BLE is stable, the Meta video is cut, and you have four hours
spare. Rank it below Elastic.

---

## Credits checklist

Grab the top block regardless — they're free and some are capped.

| Sponsor | What | How | Priority |
|---|---|---|---|
| **Meta** | $50 Model API credits | Account at dev.meta.ai + intake form | **Required** |
| **Deepgram** | $200, no card | <https://dpgr.am/hackmit26> — applied automatically | **Required** |
| **Cognition** | $1,000 Devin credits | Google Form (credits doc) + booth pickup | High |
| **The Token Company** | Compression models | thetokencompany.com (sign-in unlocked Fri 18th) | High |
| **Elastic** | 30-day Cloud trial | Self-serve, no booth | If Tier 2 |
| **SpaceXAI** | 1 mo Cursor Pro | Booth visit for the code | Free dev tooling |
| **Mintlify** | 1 mo Pro | Code `MINTHACKMIT` at signup | Optional — docs polish |
| **Warp** | 1 mo Build plan ($20) | Code `HACKMIT` at Stripe checkout | Skip unless entering |
| RunPod / Fragment / Espressif / Voloridge / ASUS / Dimensional / Regeneron / Notability | — | — | Not applicable |

⚠️ **Caps to beat:** OpenAI credits are limited to 320 participants, Notability's code to 500
redemptions, RunPod to 250 codes. Meta and Deepgram appear uncapped, but claim them in the
first hour anyway.

---

## Build delta — what actually changes in this repo

Ordered by value per hour. Nothing here touches the BLE work, which stays the riskiest and
most demo-critical item.

1. **`functions/src/` — cost instrumentation** (~1–2 h) → Token Company + Meta.
   Token/cost logging per negotiation. No user-visible change.
2. **`judge-dashboard/` — live cost counter** (~1 h) → Token Company + Ramp.
   The naive-vs-actual comparison. `index.html` is a single file today; keep it that way.
3. **Onboarding voice I/O** (~3–4 h) → Deepgram + Meta.
   Cloud Function audio endpoint, Android record/playback. Biggest demo upgrade here.
4. **`judge-dashboard/` — spoken negotiation** (~2 h) → ElevenLabs + Meta.
   Distinct voice per twin over the existing transcript. Pre-render a fallback.
5. **Elastic twin memory** (~4–6 h) → Elastic. Flag-guarded, Firestore fallback.
6. **Three pitch slides** (~30 min) → Ramp, Long Lake, Meta.
   Time-saved number, skeptic answer, guardrails. No code.

### If you have under 12 hours left

Do **1, 2 and 6** and nothing else. That's roughly four hours of work, and it adds Ramp, Long
Lake and The Token Company to your portfolio while making the Meta submission stronger.

Voice is the first thing to cut if BLE is still unstable. A working proximity trigger is worth
more than a talking one, because item 5 is the part of the demo that can't be faked.

---

## Open questions to resolve before building

1. ~~**Submission cap**~~ — **resolved: no cap.** Enter as many as you can qualify for honestly.
2. **Meta Model API prompt caching** — does `cache_control` actually work against
   `api.meta.ai`? Test before claiming it.
3. **ElevenLabs quota** — no HackMIT credit is listed for them. Confirm free-tier headroom at
   their booth before building a volume-dependent demo.
4. **Negotiation latency with voice in the loop** — `CLAUDE.md` already flags timing the
   agentic back-and-forth. STT/TTS adds seconds on both ends. Time it end-to-end once, early.
