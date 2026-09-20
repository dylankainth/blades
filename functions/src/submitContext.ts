/**
 * submitContext — callable Cloud Function (v2).
 *
 * Replaces the old multi-turn chat onboarding (formerly onboardingChat.ts)
 * with a single one-shot submission: the client sends { twinId, textDump }
 * once — a "paste anything about yourself" box, see OnboardingScreen.kt —
 * and this extracts a structured summary/interests and writes it straight
 * to twins/{twinId}. No conversation state, no multi-turn transcript, no
 * "wrap up the interview" signal to manage — one call in, one profile out.
 *
 * This is the universal context path for every user. importSocialContext.ts
 * is a separate, optional path that folds Instagram web-search results and
 * LinkedIn PDF text into the same profile.
 */
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "./lib/admin";
import { META_MODEL_API_KEY } from "./lib/secrets";
import { extractProfile, mergeFacts } from "./lib/extractProfile";
import type { TwinProfile } from "./types";

interface SubmitContextRequest {
  twinId: string;
  textDump: string;
}

interface SubmitContextResponse {
  summary: string;
  interests: string[];
}

export const submitContext = onCall<SubmitContextRequest>(
  {
    secrets: [META_MODEL_API_KEY],
    timeoutSeconds: 120,
    memory: "256MiB",
    // Onboarding is meant to be used by the authenticated device's own twin.
    enforceAppCheck: false,
  },
  async (request): Promise<SubmitContextResponse> => {
    const { twinId, textDump } = request.data ?? {};

    if (!twinId || typeof twinId !== "string") {
      throw new HttpsError("invalid-argument", "twinId is required.");
    }
    if (!textDump || typeof textDump !== "string" || !textDump.trim()) {
      throw new HttpsError("invalid-argument", "textDump is required.");
    }

    // Optional but recommended: require the caller to be signed in and
    // only allow them to submit their own twin's context.
    if (request.auth && request.auth.uid !== twinId) {
      throw new HttpsError(
        "permission-denied",
        "twinId must match the authenticated caller.",
      );
    }

    const trimmedDump = textDump.trim();

    const twinRef = db.collection("twins").doc(twinId);
    const submissionRef = db.collection("context_submissions").doc(twinId);
    const [twinSnap, submissionSnap] = await Promise.all([
      twinRef.get(),
      submissionRef.get(),
    ]);

    // A prior importSocialContext call may have already stashed scraped
    // social text on this twin — fold it back in on re-submission so
    // pasting an updated dump doesn't silently wipe out that signal.
    const existingSocialContext = twinSnap.exists
      ? ((twinSnap.data() as TwinProfile).socialContext ?? null)
      : null;

    const extracted = await extractProfile(
      META_MODEL_API_KEY.value(),
      trimmedDump,
      existingSocialContext,
    );

    // Who is this? Prefer a name already on the twin, then one the model
    // pulled out of the text, then the signed-in account's own display name
    // (Google sign-in always has one). Without this a twin built from text
    // that never states a name had no name at all — and the badge greeting
    // ("Hi Dylan"), the match teaser and the dashboard all read this field.
    const existing = twinSnap.exists ? (twinSnap.data() as TwinProfile) : null;
    const accountName =
      typeof request.auth?.token?.name === "string" ? request.auth.token.name.trim() : "";
    const accountPhoto =
      typeof request.auth?.token?.picture === "string" ? request.auth.token.picture : "";
    const resolvedName = existing?.name || extracted.name || accountName || null;
    const resolvedPhotoUrl = existing?.photoUrl || accountPhoto || null;

    const batch = db.batch();
    batch.set(
      submissionRef,
      {
        twinId,
        textDump: trimmedDump,
        ...(existingSocialContext ? { socialContext: existingSocialContext } : {}),
        updatedAt: FieldValue.serverTimestamp(),
        ...(submissionSnap.exists ? {} : { createdAt: FieldValue.serverTimestamp() }),
      },
      { merge: true },
    );
    batch.set(
      twinRef,
      {
        twinId,
        rawContext: trimmedDump,
        summary: extracted.summary || null,
        interests: extracted.interests,
        facts: mergeFacts(existing?.facts, extracted.facts),
        onboardingComplete: true,
        updatedAt: FieldValue.serverTimestamp(),
        // Only written when we actually have one — never clobber with empty.
        ...(resolvedName ? { name: resolvedName } : {}),
        ...(resolvedPhotoUrl ? { photoUrl: resolvedPhotoUrl } : {}),
        ...(twinSnap.exists ? {} : { createdAt: FieldValue.serverTimestamp() }),
      },
      { merge: true },
    );
    await batch.commit();

    return {
      summary: extracted.summary,
      interests: extracted.interests,
    };
  },
);
