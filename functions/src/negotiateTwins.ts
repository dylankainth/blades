/**
 * negotiateTwins — the pairwise LLM-to-LLM negotiation step.
 *
 * Exposes:
 *  - `runNegotiation(...)`: the actual logic, callable directly from other
 *    functions (e.g. onCheckin) without an HTTP round-trip.
 *  - `negotiateTwins`: an onCall wrapper around it, for manual testing from
 *    a client or the Firebase console / emulator shell.
 *
 * Each twin's persona (built from its Firestore profile summary) argues, in
 * a short back-and-forth over the Meta Model API, why the two people should
 * or shouldn't meet. The negotiation converges on a single plain-language
 * reason. The full transcript + reason are written to matches/{matchId}.
 *
 * IMPORTANT (per CLAUDE.md guardrails): this function only decides whether
 * to *surface* a suggestion. It never messages the other person and never
 * schedules anything — status "confirmed" here means "worth notifying the
 * humans about", not "the two people have agreed to meet". The actual
 * hand-off to a human interaction happens via notifyMatch + a client-side
 * "say hi" tap, not here.
 */
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "./lib/admin";
import { META_MODEL_API_KEY } from "./lib/secrets";
import { callMetaModel, type MetaModelMessage } from "./lib/metaModel";
import type { MatchDoc, NegotiationTurn, TwinProfile } from "./types";

const NEGOTIATION_ROUNDS = 2; // each twin speaks this many times

function personaSystemPrompt(speaker: TwinProfile, other: TwinProfile): string {
  return `You are the digital twin representing ${speaker.name || "a person"} at a
networking event. Your job in this conversation is to evaluate, honestly and
specifically, whether ${speaker.name || "your person"} should be introduced
to ${other.name || "another attendee"}, based on the two profiles below.

${speaker.name || "Your person"}'s profile:
${speaker.summary || "(no summary yet)"}
Interests: ${(speaker.interests ?? []).join(", ") || "(none extracted yet)"}

${other.name || "The other person"}'s profile:
${other.summary || "(no summary yet)"}
Interests: ${(other.interests ?? []).join(", ") || "(none extracted yet)"}

Be honest and specific, not falsely enthusiastic — if there's no real reason
to connect, say so plainly. Keep each message to 1-3 sentences. You are
negotiating with the other person's twin; work toward a shared, concrete,
plain-language reason the two humans should talk (or a clear "not a strong
match" conclusion). Never say you will message, notify, or schedule anything
yourself — that is handled outside this conversation by a human.`;
}

const MATCH_SCORE_THRESHOLD = 70;
/** A "negotiating" claim older than this is treated as a crashed run. */
const NEGOTIATION_CLAIM_TTL_MS = 3 * 60 * 1000;

const CONVERGENCE_PROMPT = `Based on this whole exchange, respond with ONLY a raw JSON object (no
markdown fences, no commentary) with exactly two fields: "score" (an integer
0-100 for how strong a reason these two people have to meet — be honest and
use the full range, most pairs should NOT score above 70) and "reason" (ONE
short plain-language sentence: if the score is high, the single best
concrete reason they should meet; if it's low, the honest reason they
probably shouldn't bother — no percentages or scores inside this sentence,
those go in the "score" field only).`;

interface Convergence {
  score: number;
  reason: string;
}

export interface RunNegotiationResult {
  matchId: string;
  reason: string | null;
  score: number | null;
  isMatch: boolean;
  transcript: NegotiationTurn[];
}

export async function runNegotiation(
  twinIdA: string,
  twinIdB: string,
  opts: { locationId?: string; apiKey: string; force?: boolean },
): Promise<RunNegotiationResult> {
  const [twinASnap, twinBSnap] = await Promise.all([
    db.collection("twins").doc(twinIdA).get(),
    db.collection("twins").doc(twinIdB).get(),
  ]);

  if (!twinASnap.exists || !twinBSnap.exists) {
    throw new Error(`One or both twins not found: ${twinIdA}, ${twinIdB}`);
  }

  const twinA = twinASnap.data() as TwinProfile;
  const twinB = twinBSnap.data() as TwinProfile;

  const matchId = [twinIdA, twinIdB].sort().join("_");
  const matchRef = db.collection("matches").doc(matchId);

  const names = {
    [twinIdA]: twinA.name || "Someone nearby",
    [twinIdB]: twinB.name || "Someone nearby",
  };
  const photoUrls = {
    [twinIdA]: twinA.photoUrl ?? null,
    [twinIdB]: twinB.photoUrl ?? null,
  };

  // Claim the pair before spending any model calls. Both phones detect each
  // other within milliseconds, so without this two onCheckin triggers run the
  // same negotiation twice in parallel. The early "negotiating" write is also
  // what lets a badge (boxState.ts) and the dashboard show the twins talking
  // while it happens. A stale claim (crashed run) can be retaken.
  const claimed = await db.runTransaction(async (tx) => {
    const snap = await tx.get(matchRef);
    if (snap.exists && !opts.force) {
      const existing = snap.data() as MatchDoc;
      const updatedMs = existing.updatedAt?.toMillis() ?? 0;
      const stale =
        existing.status === "negotiating" &&
        Date.now() - updatedMs > NEGOTIATION_CLAIM_TTL_MS;
      if (!stale) return false;
    }
    tx.set(matchRef, {
      matchId,
      twinIds: [twinIdA, twinIdB].sort(),
      locationId: opts.locationId ?? null,
      transcript: [],
      reason: null,
      score: null,
      status: "negotiating",
      names,
      photoUrls,
      shakes: {},
      metAt: null,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    return true;
  });

  if (!claimed) {
    const existing = (await matchRef.get()).data() as MatchDoc;
    return {
      matchId,
      reason: existing.reason ?? null,
      score: existing.score ?? null,
      isMatch: existing.status === "confirmed",
      transcript: existing.transcript ?? [],
    };
  }

  try {
    const transcript: NegotiationTurn[] = [];
    // Conversation history from twin A's point of view and twin B's point of
    // view are mirror images of each other (each sees the other as "user").
    const historyForA: MetaModelMessage[] = [];
    const historyForB: MetaModelMessage[] = [];

    const speak = async (
      speakerId: string,
      speakerProfile: TwinProfile,
      otherProfile: TwinProfile,
      history: MetaModelMessage[],
    ): Promise<string> => {
      const reply = await callMetaModel({
        apiKey: opts.apiKey,
        system: personaSystemPrompt(speakerProfile, otherProfile),
        messages:
          history.length > 0
            ? history
            : [
                {
                  role: "user",
                  content:
                    "Open the conversation: briefly say what you're hoping to find for your person.",
                },
              ],
      });
      transcript.push({
        speakerTwinId: speakerId,
        content: reply,
        ts: Timestamp.now(),
      });
      return reply;
    };

    for (let round = 0; round < NEGOTIATION_ROUNDS; round++) {
      const replyA = await speak(twinIdA, twinA, twinB, historyForA);
      historyForA.push({ role: "assistant", content: replyA });
      historyForB.push({ role: "user", content: replyA });

      const replyB = await speak(twinIdB, twinB, twinA, historyForB);
      historyForB.push({ role: "assistant", content: replyB });
      historyForA.push({ role: "user", content: replyB });
    }

    // Ask twin A's persona to converge on a final score + reason.
    const convergenceRaw = await callMetaModel({
      apiKey: opts.apiKey,
      system: personaSystemPrompt(twinA, twinB),
      messages: [...historyForA, { role: "user", content: CONVERGENCE_PROMPT }],
    });
    const convergence = parseConvergence(convergenceRaw);
    const isMatch = convergence.score >= MATCH_SCORE_THRESHOLD;

    const matchDoc: Partial<MatchDoc> = {
      matchId,
      twinIds: [twinIdA, twinIdB].sort() as [string, string],
      locationId: opts.locationId ?? null,
      transcript,
      // Kept regardless of outcome — the "why not" is exactly what a
      // dismissed match's detail view shows (see HomeScreen's feed).
      reason: convergence.reason || null,
      score: convergence.score,
      // "confirmed" here only means "worth surfacing to the humans" — see
      // file header. notifyMatch reacts to this and sends a push notification
      // with a "say hi" prompt; nothing is auto-messaged or auto-scheduled.
      status: isMatch ? "confirmed" : "dismissed",
      names,
      photoUrls,
      updatedAt: FieldValue.serverTimestamp() as unknown as Timestamp,
    };

    await matchRef.set(matchDoc, { merge: true });

    return {
      matchId,
      reason: matchDoc.reason ?? null,
      score: convergence.score,
      isMatch,
      transcript,
    };
  } catch (err) {
    // Release the claim so the next detection can retry, instead of leaving
    // the pair stuck on "negotiating" until the TTL expires.
    await matchRef.delete().catch((deleteErr) => {
      console.error(`Failed to release claim on ${matchId}:`, deleteErr);
    });
    throw err;
  }
}

/** Best-effort JSON parse with a safe fallback — a malformed convergence
 *  response shouldn't fail the whole negotiation, just default to "not a
 *  strong match" with whatever text came back as the reason. */
function parseConvergence(raw: string): Convergence {
  try {
    const jsonText = raw.trim().replace(/^```(?:json)?\s*|\s*```$/g, "");
    const parsed = JSON.parse(jsonText) as Partial<Convergence>;
    const score =
      typeof parsed.score === "number"
        ? Math.max(0, Math.min(100, Math.round(parsed.score)))
        : 0;
    const reason = typeof parsed.reason === "string" ? parsed.reason : "";
    return { score, reason };
  } catch (err) {
    console.error("Convergence parse failed, defaulting to no-match:", err);
    return { score: 0, reason: raw.trim().slice(0, 300) };
  }
}

interface NegotiateTwinsRequest {
  twinIdA: string;
  twinIdB: string;
  locationId?: string;
}

export const negotiateTwins = onCall<NegotiateTwinsRequest>(
  {
    secrets: [META_MODEL_API_KEY],
    timeoutSeconds: 180,
    memory: "256MiB",
  },
  async (request) => {
    const { twinIdA, twinIdB, locationId } = request.data ?? {};
    if (
      !twinIdA ||
      !twinIdB ||
      typeof twinIdA !== "string" ||
      typeof twinIdB !== "string"
    ) {
      throw new HttpsError(
        "invalid-argument",
        "twinIdA and twinIdB are required.",
      );
    }
    try {
      return await runNegotiation(twinIdA, twinIdB, {
        locationId,
        apiKey: META_MODEL_API_KEY.value(),
      });
    } catch (err) {
      throw new HttpsError(
        "internal",
        `Negotiation failed: ${err instanceof Error ? err.message : String(err)}`,
      );
    }
  },
);
