/**
 * importSocialContext — callable Cloud Function (v2).
 *
 * Folds per-provider social context into the same twins/{twinId} profile
 * that submitContext.ts writes from the text dump. The two providers work
 * very differently:
 *
 *  - instagram: NOT Graph API. Instagram's own API requires a Professional
 *    (Business/Creator) account plus a tester-role restriction on the Meta
 *    App — a non-starter for most real users. Instead, the client just
 *    sends its own Instagram `instagramHandle`, and this runs a public web
 *    search for it via the Parallel Search API (see lib/parallel.ts),
 *    folding whatever's publicly indexed (bio mentions, post excerpts,
 *    press/blog mentions) into the same extraction pipeline. This works
 *    for any handle — no OAuth, no App Review, no tester role required.
 *
 *  - linkedin: NOT an API either — LinkedIn's is invite-only partner access,
 *    not a weekend build. The client uploads a PDF of the twin's own
 *    LinkedIn profile (LinkedIn's native "Save to PDF" export) as base64;
 *    this extracts its text with pdf-parse (see lib/linkedinPdf.ts) and
 *    folds it into the same pipeline. No OAuth, no App Review, works for
 *    anyone with a LinkedIn account.
 */
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "./lib/admin";
import { META_MODEL_API_KEY, PARALLEL_API_KEY } from "./lib/secrets";
import { extractProfile, mergeFacts } from "./lib/extractProfile";
import { ParallelApiError, searchWeb } from "./lib/parallel";
import { LinkedinPdfError, extractLinkedinPdfText } from "./lib/linkedinPdf";
import type { TwinProfile } from "./types";

type SocialProvider = "instagram" | "linkedin";
/** Legacy `[[facebook]]` sections may still exist on older twin docs. */
type SocialSectionKey = SocialProvider | "facebook";

interface ImportSocialContextRequest {
  twinId: string;
  provider: SocialProvider;
  /** Required when provider is "instagram" — the twin's own handle (leading "@" optional). */
  instagramHandle?: string;
  /** Required when provider is "linkedin" — base64 of the exported profile PDF. */
  pdfBase64?: string;
}

interface ImportSocialContextResponse {
  imported: boolean;
  itemCount: number;
  message: string;
  summary?: string;
  interests?: string[];
}

type SocialSections = Partial<Record<SocialSectionKey, string>>;

/**
 * `socialContext` on twins/{twinId} is one string holding both providers'
 * text, so importing Instagram doesn't wipe out a previously-imported
 * LinkedIn pull (or vice versa). Sections are marked with a plain
 * `[[provider]]` line so they can be parsed back out and individually
 * replaced on re-import. `facebook` is still recognized so an old section
 * isn't dropped when another provider is re-imported.
 */
function parseSocialSections(existing: string | null | undefined): SocialSections {
  if (!existing) return {};
  const sections: SocialSections = {};
  for (const part of existing.split(/\n\n(?=\[\[(?:facebook|instagram|linkedin)\]\])/)) {
    const match = part.match(/^\[\[(facebook|instagram|linkedin)\]\]\n([\s\S]*)$/);
    if (match) {
      sections[match[1] as SocialSectionKey] = match[2];
    }
  }
  return sections;
}

function serializeSocialSections(sections: SocialSections): string | null {
  const serialized = Object.entries(sections)
    .filter(([, text]) => text && text.trim().length > 0)
    .map(([provider, text]) => `[[${provider}]]\n${(text as string).trim()}`)
    .join("\n\n");
  return serialized || null;
}

export const importSocialContext = onCall<ImportSocialContextRequest>(
  {
    secrets: [META_MODEL_API_KEY, PARALLEL_API_KEY],
    timeoutSeconds: 120,
    memory: "256MiB",
    enforceAppCheck: false,
  },
  async (request): Promise<ImportSocialContextResponse> => {
    const { twinId, provider, instagramHandle, pdfBase64 } = request.data ?? {};

    if (!twinId || typeof twinId !== "string") {
      throw new HttpsError("invalid-argument", "twinId is required.");
    }
    if (provider !== "instagram" && provider !== "linkedin") {
      throw new HttpsError(
        "invalid-argument",
        'provider must be "instagram" or "linkedin".',
      );
    }
    const cleanedHandle = instagramHandle?.trim().replace(/^@/, "");
    if (provider === "instagram" && !cleanedHandle) {
      throw new HttpsError(
        "invalid-argument",
        "instagramHandle is required for the instagram provider.",
      );
    }
    if (provider === "linkedin" && (!pdfBase64 || typeof pdfBase64 !== "string")) {
      throw new HttpsError(
        "invalid-argument",
        "pdfBase64 is required for the linkedin provider.",
      );
    }
    if (request.auth && request.auth.uid !== twinId) {
      throw new HttpsError(
        "permission-denied",
        "twinId must match the authenticated caller.",
      );
    }

    let contentChunks: string[];
    try {
      if (provider === "instagram") {
        contentChunks = await searchWeb(
          PARALLEL_API_KEY.value(),
          `Find publicly available information about the Instagram account ` +
            `"@${cleanedHandle}" — bio details, what they post about, ` +
            `interests, occupation, location, or anything else that gives a ` +
            `sense of who they are.`,
          [`instagram.com/${cleanedHandle}`, `"${cleanedHandle}" instagram`],
        );
      } else {
        const text = await extractLinkedinPdfText(pdfBase64 as string);
        contentChunks = [text];
      }
    } catch (err) {
      // A Parallel search failure (rate limit, no credit, transient error)
      // and an unreadable/oversized LinkedIn PDF are handled the same way
      // — either way the text dump still covers the twin's context.
      if (err instanceof ParallelApiError || err instanceof LinkedinPdfError) {
        console.warn(
          `Social import unavailable for twin ${twinId} (${provider}): ${err.message}`,
        );
        return {
          imported: false,
          itemCount: 0,
          message:
            provider === "instagram"
              ? "Couldn't search the web for that Instagram handle right now — no " +
                "worries, your text dump is still used for your twin's context."
              : `${err.message} No worries, your text dump is still used for your twin's context.`,
        };
      }
      throw new HttpsError(
        "internal",
        `${
          provider === "instagram" ? "Parallel search" : "LinkedIn PDF"
        } request failed: ${err instanceof Error ? err.message : String(err)}`,
      );
    }

    if (contentChunks.length === 0) {
      return {
        imported: true,
        itemCount: 0,
        message:
          provider === "instagram"
            ? "Connected, but no public web results were found for that handle."
            : "The PDF was read, but no usable text was found in it.",
      };
    }

    const twinRef = db.collection("twins").doc(twinId);
    const submissionRef = db.collection("context_submissions").doc(twinId);
    const [twinSnap, submissionSnap] = await Promise.all([
      twinRef.get(),
      submissionRef.get(),
    ]);

    const existingTwin = twinSnap.exists ? (twinSnap.data() as TwinProfile) : null;
    const sections = parseSocialSections(existingTwin?.socialContext ?? null);
    sections[provider] = contentChunks.join("\n\n");
    const mergedSocialContext = serializeSocialSections(sections);

    const extracted = await extractProfile(
      META_MODEL_API_KEY.value(),
      existingTwin?.rawContext ?? null,
      mergedSocialContext,
    );

    const batch = db.batch();
    batch.set(
      submissionRef,
      {
        twinId,
        socialContext: mergedSocialContext,
        updatedAt: FieldValue.serverTimestamp(),
        ...(submissionSnap.exists
          ? {}
          : { createdAt: FieldValue.serverTimestamp(), textDump: "" }),
      },
      { merge: true },
    );
    batch.set(
      twinRef,
      {
        twinId,
        socialContext: mergedSocialContext,
        summary: extracted.summary || null,
        interests: extracted.interests,
        facts: mergeFacts(existingTwin?.facts, extracted.facts),
        updatedAt: FieldValue.serverTimestamp(),
        // Only set if the model actually extracted one — don't clobber a
        // name set some other way with an empty value.
        ...(extracted.name ? { name: extracted.name } : {}),
        ...(twinSnap.exists ? {} : { createdAt: FieldValue.serverTimestamp() }),
      },
      { merge: true },
    );
    await batch.commit();

    return {
      imported: true,
      itemCount: contentChunks.length,
      message:
        provider === "instagram"
          ? `Found ${contentChunks.length} public web result${
              contentChunks.length === 1 ? "" : "s"
            } about your Instagram and added them to your twin's context.`
          : "Read your LinkedIn PDF and added it to your twin's context.",
      summary: extracted.summary,
      interests: extracted.interests,
    };
  },
);
