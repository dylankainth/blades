/**
 * importSocialContext — callable Cloud Function (v2).
 *
 * Tier B of the two-tier context model (see CLAUDE.md): folds richer,
 * per-provider social context into the same twins/{twinId} profile that
 * submitContext.ts (Tier A) writes. The two providers work very
 * differently:
 *
 *  - facebook: real Facebook post text via Graph API's `GET /me/posts`,
 *    using a user access token from classic Facebook Login requesting
 *    `user_posts` (client sends `accessToken`). `user_posts` is a Standard
 *    Access permission, so this ONLY works for accounts that have a role
 *    (Admin/Developer/Tester) on the Meta App while it's in Development
 *    Mode — any other account's token gets rejected by Graph API with a
 *    permission error. That's the expected outcome for real attendees,
 *    not a bug: this function treats it as a normal "not available for
 *    this account" result (`imported: false`) rather than a hard failure.
 *
 *  - instagram: NOT Graph API. Instagram's own API requires a Professional
 *    (Business/Creator) account plus that same tester-role restriction on
 *    top — a non-starter for most real users. Instead, the client just
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
import { extractProfile } from "./lib/extractProfile";
import { GraphApiError, fetchFacebookPostText } from "./lib/graphApi";
import { ParallelApiError, searchWeb } from "./lib/parallel";
import { LinkedinPdfError, extractLinkedinPdfText } from "./lib/linkedinPdf";
import type { TwinProfile } from "./types";

type SocialProvider = "facebook" | "instagram" | "linkedin";

interface ImportSocialContextRequest {
  twinId: string;
  provider: SocialProvider;
  /** Required when provider is "facebook" — the token from Facebook Login. */
  accessToken?: string;
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

type SocialSections = Partial<Record<SocialProvider, string>>;

/**
 * `socialContext` on twins/{twinId} is one string holding both providers'
 * text, so importing Instagram doesn't wipe out a previously-imported
 * Facebook pull (or vice versa). Sections are marked with a plain
 * `[[provider]]` line so they can be parsed back out and individually
 * replaced on re-import.
 */
function parseSocialSections(existing: string | null | undefined): SocialSections {
  if (!existing) return {};
  const sections: SocialSections = {};
  for (const part of existing.split(/\n\n(?=\[\[(?:facebook|instagram|linkedin)\]\])/)) {
    const match = part.match(/^\[\[(facebook|instagram|linkedin)\]\]\n([\s\S]*)$/);
    if (match) {
      sections[match[1] as SocialProvider] = match[2];
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
    const { twinId, provider, accessToken, instagramHandle, pdfBase64 } = request.data ?? {};

    if (!twinId || typeof twinId !== "string") {
      throw new HttpsError("invalid-argument", "twinId is required.");
    }
    if (provider !== "facebook" && provider !== "instagram" && provider !== "linkedin") {
      throw new HttpsError(
        "invalid-argument",
        'provider must be "facebook", "instagram", or "linkedin".',
      );
    }
    if (provider === "facebook" && (!accessToken || typeof accessToken !== "string")) {
      throw new HttpsError(
        "invalid-argument",
        "accessToken is required for the facebook provider.",
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
      if (provider === "facebook") {
        contentChunks = await fetchFacebookPostText(accessToken as string);
      } else if (provider === "instagram") {
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
      // Expected for the vast majority of real Facebook users — see file
      // header. No tester/role on the Meta App means Graph API rejects the
      // token with a permission error. That's a normal, reportable outcome
      // here, not a crash. A Parallel search failure (rate limit, no
      // credit, transient error) and an unreadable/oversized LinkedIn PDF
      // are handled the same way for symmetry — either way the text dump
      // still covers the twin's context.
      if (err instanceof GraphApiError || err instanceof ParallelApiError || err instanceof LinkedinPdfError) {
        console.warn(
          `Social import unavailable for twin ${twinId} (${provider}): ${err.message}`,
        );
        return {
          imported: false,
          itemCount: 0,
          message:
            provider === "facebook"
              ? "This account isn't enabled for Facebook post import yet (it needs a " +
                "tester role on the Meta app) — no worries, your text dump is still " +
                "used for your twin's context."
              : provider === "instagram"
                ? "Couldn't search the web for that Instagram handle right now — no " +
                  "worries, your text dump is still used for your twin's context."
                : `${err.message} No worries, your text dump is still used for your twin's context.`,
        };
      }
      throw new HttpsError(
        "internal",
        `${
          provider === "facebook" ? "Graph API" : provider === "instagram" ? "Parallel search" : "LinkedIn PDF"
        } request failed: ${err instanceof Error ? err.message : String(err)}`,
      );
    }

    if (contentChunks.length === 0) {
      return {
        imported: true,
        itemCount: 0,
        message:
          provider === "facebook"
            ? "Connected, but no recent post text was found to import."
            : provider === "instagram"
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
        provider === "facebook"
          ? `Pulled ${contentChunks.length} recent Facebook post${
              contentChunks.length === 1 ? "" : "s"
            } into your twin's context.`
          : provider === "instagram"
            ? `Found ${contentChunks.length} public web result${
                contentChunks.length === 1 ? "" : "s"
              } about your Instagram and added them to your twin's context.`
            : "Read your LinkedIn PDF and added it to your twin's context.",
      summary: extracted.summary,
      interests: extracted.interests,
    };
  },
);
