# Digital Twins for Human Connection — HackMIT

## Concept

Build digital twins (AI agents) of individual people, created quickly via
conversational onboarding (not slow OAuth scraping). Twins interact with each
other on your behalf to surface people you should meet, then notify you when
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
   - Fix: cut to at most one or two data sources, or skip scraping entirely.
     Build the twin via a ~90-second conversational onboarding with Claude
     ("tell me about yourself, what are you hoping to get out of this
     weekend"). Faster to build, never breaks live, and feels more personal
     than "we read your LinkedIn."

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
   iOS/web is not a weekend build; OS permission plumbing alone eats the
   available time.
   - Fix: fake the physical/sensor layer, keep the intelligence layer real.
     Use QR check-in stations around the venue, or a simple "I'm near booth
     X" web check-in, to trigger the real matching engine and a real push
     notification. Nobody will know (or care) how proximity was detected —
     they'll remember what the phone said.

## What to actually build, in priority order

1. **Fast twin creation** — conversational onboarding flow with Claude. This
   is the opening beat of the demo.
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
5. **Proximity trigger** — fake sensor (QR/check-in) that also *drives* the
   matching engine: a check-in triggers live pairwise negotiation against
   whoever else is currently at that location, not a lookup against a
   precomputed global shortlist.

## Interface notes (priority: interface is the product)

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
