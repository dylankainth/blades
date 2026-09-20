/**
 * Shared context -> structured-profile extraction.
 *
 * Used by both submitContext.ts (Tier A: the user's own pasted text dump)
 * and importSocialContext.ts (Tier B: scraped Instagram/Facebook caption
 * text, tester/role accounts only — see CLAUDE.md's two-tier context
 * model) since both ultimately need to turn freeform text into the same
 * name/summary/interests shape that twins/{twinId} stores and
 * negotiateTwins.ts reads.
 */
import { callMetaModel } from "./metaModel";

const PROFILE_EXTRACTION_SYSTEM_PROMPT = `You will be given freeform text about a person, meant to build their profile
for a networking-matchmaking app. It may include a "self-written context"
section (their own words, most authoritative) and/or a "scraped social
media" section (captions/posts pulled from their own Instagram/Facebook —
secondary signal, useful for texture but weight the self-written section
higher if the two disagree).

Extract a JSON object with exactly three fields: "name" (the person's first
name, or first name + last initial if given — just their name as they
introduced themselves, or "" if none is discernible), "summary" (a 1-3
sentence plain-language description of who this person is, what they're
working on, and what they'd be looking for out of a networking event), and
"interests" (an array of 3-8 short lowercase tags/keywords, e.g. "devops",
"looking for cofounder", "rock climbing"). Respond with ONLY the raw JSON
object, no markdown fences, no commentary.`;

export interface ExtractedProfile {
  name: string;
  summary: string;
  interests: string[];
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
      ? `--- Scraped social media captions/posts (Instagram/Facebook) ---\n${socialContext.trim()}`
      : null,
  ].filter((s): s is string => !!s);

  // Nothing to extract from at all — shouldn't normally happen (callers
  // only invoke this once they have at least one source), but return an
  // empty profile rather than making a pointless model call.
  if (sections.length === 0) {
    return { name: "", summary: "", interests: [] };
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
    };
  } catch (err) {
    console.error("Profile extraction failed, using empty profile:", err);
    return { name: "", summary: "", interests: [] };
  }
}
