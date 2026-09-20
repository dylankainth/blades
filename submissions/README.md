# Submissions

Decks and paste-ready copy for the tracks that need no code.

## Decks

| File | Track | Slides |
|---|---|---|
| `ramp.html` | Ramp — Save Time. Save Money. | 5 |
| `long-lake.html` | Long Lake — Convince a Non-Believer | 6 |

Built on the same tokens as the Android app (`ui/theme/Color.kt`) and the judge
dashboard, so a deck on the projector and the phone in your hand read as one product.

**Present:** open in a browser, `←` / `→` to navigate.
**Export PDF:** press `P`, or Print → Landscape → Margins: None → **Background
graphics: ON** → Save as PDF.

### Two things that make these not-a-normal-deck

**1. The Room** (`room.js`) — the motif on the title and closing slides. A thousand
faint dots, then every possible introduction between them as an unreadable hairball,
which burns off to leave the handful of amber connections that actually happened. It is
the `499,500 → 19` argument as motion rather than as a bullet, and it is specific to
this product in a way a stock gradient isn't. Replays each time the slide comes into
view, so it lands again if you walk back to it during Q&A.

**2. Live numbers** (`live.js`) — the Ramp hero slide reads its count off the same
`judge_feed` collection the judge dashboard does, **live, while you present**. If a
negotiation happens mid-pitch the number moves on the screen. Long Lake's proof slide
lights its indicator only once a real dismissed pairing is confirmed in the database.

> **The numbers written in the HTML are the fallback, and they are correct on their
> own.** If the venue wifi dies, Firestore is slow, or anonymous auth gets rate-limited,
> every figure keeps its static value and the "live" bulb simply stays grey — you get a
> normal, accurate deck. Nothing on screen breaks, and nothing claims to be live when it
> isn't. **This is why there is no longer a hardcoded number to remember to update.**

Reduced-motion is respected throughout: the motif renders its end state directly and
the entrance stagger is disabled. Printing forces every reveal visible, so an exported
PDF never captures a half-played animation.

---

## Ramp — paste-ready

**One-liner**
Klick finds the three people at a 1,000-person event actually worth your time, using
90 seconds of yours instead of your whole weekend.

**Submission text**

> Finding the handful of people worth meeting at a large event costs you the entire
> event. Covering a room of a thousand means roughly 200 conversations you will never
> have, and the person solving your exact problem was forty feet away on Saturday night.
>
> Klick builds a digital twin of you from one short spoken conversation. When you
> physically cross paths with someone, the two twins negotiate and surface one specific
> reason to talk — or honestly conclude there isn't one.
>
> The saving is structural. A system that precomputed a global shortlist would reason
> about 499,500 pairings for a thousand attendees. Because matching is gated by physical
> proximity rather than a global index, Klick ran 19. That is 99.996% of the work never
> happening, and the cost scales with foot traffic rather than attendance. Every
> dismissed pairing is measured too — what a rejection costs is the number this is
> judged against. The live totals are on our judge dashboard.

---

## Long Lake — paste-ready

**One-liner**
The AI social product a skeptic would actually try: the twins do the awkward
coordination humans avoid, then hand the decision back to you.

**Submission text**

> The skeptic's objection to AI social products is fair: nobody wants a bot deciding who
> their friends are. We designed Klick as the answer to that objection rather than
> another example of it.
>
> Twins never decide, never message, and never book. They do the part humans actually
> avoid — filtering a thousand strangers, surfacing shared context, and saying the
> honest and inconvenient thing when there is no real reason to meet. Then they hand the
> connection back to a person.
>
> Every guardrail is something you can watch, not a claim in a pitch:
>
> - **A reason, never a score.** The output is one plain sentence about why two people
>   should talk. No percentage, no "87% match."
> - **Dismissals stay private.** When twins decide there's no reason to meet, the names,
>   reasoning and transcript are never published — not even to judges. Open our dashboard
>   and click a dismissed pairing; it tells you why it won't show you.
> - **Both sides approve before names appear.** Identity stays anonymised until two
>   people have each said yes.
> - **"Say hi" is a human tap.** Nothing is ever sent on your behalf.
>
> A skeptic doesn't need to believe in AI. They need one specific, true reason to talk to
> the person near them — and to still be the one who decides.

---

## Main track eligibility

HackMIT's own tracks are **healthcare, sustainability, education, entertainment**.

| Track | Fit | Notes |
|---|---|---|
| **Education** | ✅ Worth entering | `CLAUDE.md` already identified this during ideation: twins matching complementary skills, schedules and working styles for project teams and study groups. Klick on a campus is that, unchanged — connecting students to the people, labs and projects they'd otherwise never find. Submission copy, not code. |
| **Entertainment** | ⚠️ Arguable | The strongest angle is the badge: a physical creature that reacts, squishes when poked, and strobes when a match is revealed. It's a playful social object, not a utility. Weaker than Education — enter only if you can tell that story without straining. |
| **Healthcare** | ❌ | No honest fit. |
| **Sustainability** | ❌ | No honest fit. |

Overall **1st / 2nd / 3rd** need no separate entry — every project is judged.
