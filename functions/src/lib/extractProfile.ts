/**
 * Shared context -> structured-profile extraction.
 *
 * Used by both submitContext.ts (the user's own pasted text dump) and
 * importSocialContext.ts (Instagram web-search results / LinkedIn PDF
 * text) since both ultimately need to turn freeform text into the same
 * name/summary/interests shape that twins/{twinId} stores and
 * negotiateTwins.ts reads.
 */
import { callMetaModel } from "./metaModel";
import type { TwinFact } from "../types";

const PROFILE_EXTRACTION_SYSTEM_PROMPT = `You will be given freeform text about a person, meant to build their profile
for a networking-matchmaking app. It may include a "self-written context"
section (their own words, most authoritative) and/or a "scraped social
media" section (Instagram web-search results or a LinkedIn PDF —
secondary signal, useful for texture but weight the self-written section
higher if the two disagree).

Extract a JSON object with exactly four fields: "name" (the person's first
name, or first name + last initial if given — just their name as they
introduced themselves, or "" if none is discernible), "summary" (a 1-3
sentence plain-language description of who this person is, what they're
working on, and what they'd be looking for out of a networking event),
"interests" (an array of 3-8 short lowercase tags/keywords, e.g. "devops",
"looking for cofounder", "rock climbing"), and "facts" (an array of 5-6
objects, each with a short "category" label and a one-sentence "detail" —
distinct, specific, individually-readable observations pulled from the
text, e.g. {"category": "Current project", "detail": "Building a
devops pipeline for a student club, stuck on the CI step."},
{"category": "Looking for", "detail": "A technical co-founder with backend
experience."}. Cover a spread of different angles when the text supports
it — background/career, current project, personal interests, what they're
looking for, communication style, a specific detail or anecdote they
mentioned — rather than several facts restating the same thing. If the
text is too thin to support 5-6 distinct facts, return fewer rather than
inventing filler.). Respond with ONLY the raw JSON object, no markdown
fences, no commentary.`;

export interface ExtractedProfile {
  name: string;
  summary: string;
  interests: string[];
  facts: TwinFact[];
}

/**
 * Folds a fresh extraction into whatever facts already exist on the twin,
 * preserving anything the user hand-edited (see TwinFact.edited) instead of
 * silently clobbering it with the model's next guess. Matched by category
 * (case-insensitive) — an edited "Current project" fact stays put even if
 * re-extraction would produce a new one under the same label; freshly
 * extracted categories that don't collide with an edited one pass through
 * untouched. A user-deleted fact simply isn't in `existing` anymore, so
 * this can't resurrect it unless a later resubmission happens to produce
 * the same category again — an acceptable trade-off for a hackathon build.
 */
export function mergeFacts(
  existing: TwinFact[] | undefined,
  extracted: TwinFact[],
): TwinFact[] {
  const edited = (existing ?? []).filter((f) => f.edited);
  const editedCategories = new Set(edited.map((f) => f.category.trim().toLowerCase()));
  const fresh = extracted.filter(
    (f) => !editedCategories.has(f.category.trim().toLowerCase()),
  );
  return [...edited, ...fresh];
}

/**
 * Combines the self-written dump with optional scraped social text into one
 * labeled prompt, calls Muse Spark once, and parses the JSON result.
 * Best-effort: on any parse failure this falls back to an empty profile
 * rather than throwing — a bad extraction should never block onboarding or
 * a social-import attempt from otherwise succeeding.
 */
export async function extractProfile(
  apiKey: string,
  rawContext?: string | null,
  socialContext?: string | null,
): Promise<ExtractedProfile> {
  const sections = [
    rawContext?.trim()
      ? `--- Self-written context (from the person directly) ---\n${rawContext.trim()}`
      : null,
    socialContext?.trim()
      ? `--- Scraped social media captions/posts (Instagram/LinkedIn) ---\n${socialContext.trim()}`
      : null,
  ].filter((s): s is string => !!s);

  // Nothing to extract from at all — shouldn't normally happen (callers
  // only invoke this once they have at least one source), but return an
  // empty profile rather than making a pointless model call.
  if (sections.length === 0) {
    return { name: "", summary: "", interests: [], facts: [] };
  }

  try {
    const raw = await callMetaModel({
      apiKey,
      system: PROFILE_EXTRACTION_SYSTEM_PROMPT,
      messages: [{ role: "user", content: sections.join("\n\n") }],
      maxTokens: 4096,
    });
    const jsonText = raw.trim().replace(/^```(?:json)?\s*|\s*```$/g, "");
    const parsed = JSON.parse(jsonText) as Partial<ExtractedProfile>;
    return {
      name: typeof parsed.name === "string" ? parsed.name : "",
      summary: typeof parsed.summary === "string" ? parsed.summary : "",
      interests: Array.isArray(parsed.interests)
        ? parsed.interests.filter((i): i is string => typeof i === "string")
        : [],
      facts: Array.isArray(parsed.facts)
        ? parsed.facts
            .filter(
              (f): f is TwinFact =>
                !!f &&
                typeof f === "object" &&
                typeof (f as TwinFact).category === "string" &&
                typeof (f as TwinFact).detail === "string",
            )
            .map((f) => ({ category: f.category, detail: f.detail }))
        : [],
    };
  } catch (err) {
    console.error("Profile extraction failed, using empty profile:", err);
    return { name: "", summary: "", interests: [], facts: [] };
  }
}
