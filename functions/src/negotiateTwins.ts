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

function personaSystemPrompt(
  speaker: TwinProfile,
  other: TwinProfile,
): string {
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

const CONVERGENCE_PROMPT = `Based on this whole exchange, state in ONE short plain-language sentence
(no percentages, no scores) the single best concrete reason these two people
should meet — or, if you both concluded it's not a strong match, say "NO_MATCH"
followed by a one-sentence reason why not. Respond with just that sentence.`;

export interface RunNegotiationResult {
  matchId: string;
  reason: string | null;
  isMatch: boolean;
  transcript: NegotiationTurn[];
}

export async function runNegotiation(
  twinIdA: string,
  twinIdB: string,
  opts: { locationId?: string; apiKey: string },
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

  // Ask twin A's persona to converge on a final plain-language verdict.
  const convergence = await callMetaModel({
    apiKey: opts.apiKey,
    system: personaSystemPrompt(twinA, twinB),
    messages: [...historyForA, { role: "user", content: CONVERGENCE_PROMPT }],
  });

  const isMatch = !convergence.trim().toUpperCase().startsWith("NO_MATCH");
  const reason = isMatch
    ? convergence.trim()
    : convergence.replace(/^NO_MATCH:?\s*/i, "").trim() || null;

  const matchDoc: Partial<MatchDoc> = {
    matchId,
    twinIds: [twinIdA, twinIdB].sort() as [string, string],
    locationId: opts.locationId ?? null,
    transcript,
    reason: isMatch ? reason : null,
    // "confirmed" here only means "worth surfacing to the humans" — see
    // file header. notifyMatch reacts to this and sends a push notification
    // with a "say hi" prompt; nothing is auto-messaged or auto-scheduled.
    status: isMatch ? "confirmed" : "dismissed",
    names: {
      [twinIdA]: twinA.name || "Someone nearby",
      [twinIdB]: twinB.name || "Someone nearby",
    },
    photoUrls: {
      [twinIdA]: twinA.photoUrl ?? null,
      [twinIdB]: twinB.photoUrl ?? null,
    },
    updatedAt: FieldValue.serverTimestamp() as unknown as Timestamp,
  };

  await matchRef.set(
    { ...matchDoc, createdAt: FieldValue.serverTimestamp() },
    { merge: true },
  );

  return { matchId, reason: matchDoc.reason ?? null, isMatch, transcript };
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
    if (!twinIdA || !twinIdB || typeof twinIdA !== "string" || typeof twinIdB !== "string") {
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
