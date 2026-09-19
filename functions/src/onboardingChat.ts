/**
 * onboardingChat — callable Cloud Function (v2).
 *
 * Stateless per-turn conversational onboarding. The client sends { twinId,
 * message }; we load the prior transcript from Firestore, append the new
 * user turn, call the Meta Model API (Muse Spark) with the full history + a
 * system prompt aimed at a ~90-second friendly interview, append the
 * assistant reply, and return it.
 *
 * All state lives in Firestore (`onboarding_sessions/{twinId}`) — nothing is
 * held in memory between invocations, so this scales fine across cold starts.
 *
 * Uses the same Meta Model API client as negotiateTwins.ts, so the whole
 * app runs on one model vendor (Muse Spark) rather than splitting onboarding
 * (Claude) from negotiation (Muse) — see CLAUDE.md Stack decisions.
 */
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "./lib/admin";
import { META_MODEL_API_KEY } from "./lib/secrets";
import { callMetaModel, type MetaModelMessage } from "./lib/metaModel";
import type { OnboardingSession, OnboardingTurn, TwinProfile } from "./types";

const ONBOARDING_SYSTEM_PROMPT = `You are the onboarding voice for a person's "digital twin" — a lightweight AI
profile that will later represent them to other twins at an event, for the
sole purpose of surfacing a handful of people worth meeting in person.

Your job: run a warm, casual, ~90-second conversational interview (roughly
4-6 short exchanges). Ask things like: "tell me a bit about yourself", "what
are you hoping to get out of this weekend", "what are you working on or
stuck on right now", "what kind of person would be a great person to run
into here". Keep questions short, one at a time, and friendly — this should
feel like chatting with a curious friend, not filling out a form.

Do not be exhaustive. Once you have a decent picture of who they are, what
they're working on, and what they're looking for, say so warmly and wrap up
— do not keep interrogating them. Never claim to take any action on their
behalf (no scheduling, no messaging other people) — you are only building
their profile.`;

interface OnboardingChatRequest {
  twinId: string;
  message: string;
}

interface OnboardingChatResponse {
  reply: string;
  turnCount: number;
}

export const onboardingChat = onCall<OnboardingChatRequest>(
  {
    secrets: [META_MODEL_API_KEY],
    timeoutSeconds: 120,
    memory: "256MiB",
    // Onboarding is meant to be used by the authenticated device's own twin.
    enforceAppCheck: false,
  },
  async (request): Promise<OnboardingChatResponse> => {
    const { twinId, message } = request.data ?? {};

    if (!twinId || typeof twinId !== "string") {
      throw new HttpsError("invalid-argument", "twinId is required.");
    }
    if (!message || typeof message !== "string" || !message.trim()) {
      throw new HttpsError("invalid-argument", "message is required.");
    }

    // Optional but recommended: require the caller to be signed in (Firebase
    // Auth anonymous sign-in per device) and only allow them to drive their
    // own twin's onboarding session.
    if (request.auth && request.auth.uid !== twinId) {
      throw new HttpsError(
        "permission-denied",
        "twinId must match the authenticated caller.",
      );
    }

    const sessionRef = db.collection("onboarding_sessions").doc(twinId);
    const sessionSnap = await sessionRef.get();
    const existing = sessionSnap.exists
      ? (sessionSnap.data() as OnboardingSession)
      : null;
    const priorTurns: OnboardingTurn[] = existing?.turns ?? [];

    const userTurn: OnboardingTurn = {
      role: "user",
      content: message.trim(),
      ts: Timestamp.now(),
    };

    const metaModelMessages: MetaModelMessage[] = [
      ...priorTurns.map((t) => ({
        role: t.role,
        content: t.content,
      })),
      { role: "user", content: userTurn.content },
    ];

    let reply: string;
    try {
      reply = await callMetaModel({
        apiKey: META_MODEL_API_KEY.value(),
        system: ONBOARDING_SYSTEM_PROMPT,
        messages: metaModelMessages,
        maxTokens: 4096,
      });
      if (!reply) {
        throw new HttpsError(
          "internal",
          "Muse Spark returned no text content for this turn.",
        );
      }
    } catch (err) {
      if (err instanceof HttpsError) throw err;
      throw new HttpsError(
        "internal",
        `Meta Model API error: ${err instanceof Error ? err.message : String(err)}`,
      );
    }

    const assistantTurn: OnboardingTurn = {
      role: "assistant",
      content: reply,
      ts: Timestamp.now(),
    };

    const updatedTurns = [...priorTurns, userTurn, assistantTurn];

    const batch = db.batch();
    batch.set(
      sessionRef,
      {
        twinId,
        turns: updatedTurns,
        updatedAt: FieldValue.serverTimestamp(),
        ...(existing ? {} : { createdAt: FieldValue.serverTimestamp() }),
      },
      { merge: true },
    );

    // Keep a lightweight mirror on the twin profile itself so other
    // functions (matching, negotiation) don't need to read the full
    // transcript — just the latest summary. We don't call Claude a second
    // time to summarize here to keep this function fast; a fuller summary
    // pass can be layered in later (e.g. once onboarding completes).
    const twinRef = db.collection("twins").doc(twinId);
    batch.set(
      twinRef,
      {
        twinId,
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );

    await batch.commit();

    return { reply, turnCount: updatedTurns.length };
  },
);
