# Badge product shot — Reve brief

A hero image of the badge for the top of the Devpost / submission page. This is the
one technique from the Cindy Zhu guides that pays off at this stage: the badge is the
only physical artefact we have, and a submission page with a real product shot reads as
shipped rather than as a repo.

**~15 minutes.** You need a phone photo of the badge and a Reve account
([reve.com](https://reve.com) → Go to app → Build from scratch).

---

## Step 1 — take the photo

Any phone shot works. Make it easy on the model:

- Badge on a plain surface, screen **on and showing the mascot** (that's the character,
  and it's the bit people remember).
- Even light, no hard shadow across the screen.
- Shoot slightly above and to one side so the enclosure reads as a 3D object rather
  than a flat rectangle.
- Get close enough that the badge fills most of the frame.

## Step 2 — paste this into Claude with the photo attached

> You are my creative director and product photographer. I have attached a photo of my
> product: a small 3D-printed wearable badge built on an ESP32-S3 with a round colour
> display. It shows an animated character, it reacts when you poke it, and it lights up
> when the person wearing it has been matched with someone nearby worth meeting. It is
> part of Klick, an app where AI twins of two people negotiate whether they should meet.
>
> The audience is hackathon judges and engineers. The feeling should be warm, tactile
> and a little playful — a friendly object, not a gadget. Our palette is a warm
> off-white background (#EEECE9), near-black (#1C1B1A) and a single warm amber accent
> (#C97B45). Our typeface is Space Grotesk.
>
> Study the product and design one scroll-stopping product shot. Give me, in order:
>
> 1. The concept: studio scene, surface, props, lighting and mood, in one tight paragraph.
> 2. The palette: 3–4 hex codes that flatter it, starting from ours above.
> 3. Five headline options, short and on-brand, no clichés. Mark your favourite.
> 4. The Reve prompt: one detailed image prompt I can paste straight into Reve to render
>    this in 4K — describing the product, its placement, the lighting, the camera angle
>    and the palette. Keep my product true to the photo and never invent a logo or brand
>    name.

## Step 3 — render and refine

Paste the returned Reve prompt **and** your photo into Reve 2.0 and generate. Treat the
first render as 90% done: draw a shadow or prop directly onto the image for the last
10% rather than rerolling the whole thing.

---

## Where it goes

- Top of the Devpost / submission page
- The Meta write-up (a working prototype reads better with a picture of the thing)
- Optional: a title slide in `ramp.html` / `long-lake.html`

## What we deliberately skipped

The other Cindy Zhu guides don't earn their time this weekend:

- **Scroll animation** (Flow → EZGIF → GSAP) — days of work, needs a landing page we
  don't have, and no judge sees it.
- **5 design websites** (Savee, Typewolf, Happy Hues, fffuel, Adfolio) — a research tool
  for deciding a direction. We already have one, in `ui/theme/Color.kt`.
- **shadcn/ui, pattern.css** — React and CSS libraries. The app is Jetpack Compose, so
  they don't apply; the dashboard borrowed the *ideas* (quiet dot texture, card system)
  without taking the dependencies.
- **Google Fonts, Lucide** — already effectively in use: Space Grotesk ships in the app
  and now loads on the dashboard, and the icon set is small enough to inline.
