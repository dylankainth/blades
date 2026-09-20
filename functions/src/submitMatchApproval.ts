/**
 * submitMatchApproval — callable Cloud Function (v2).
 *
 * The second, human consent gate on top of the AI's "confirmed" — see
 * CLAUDE.md guardrails and negotiateTwins.ts's file header. A confirmed
 * match only means "the negotiation thinks this is worth surfacing"; real
 * identity (unblurred name) and the BLE radar screen only unlock once BOTH
 * people have independently tapped Approve on the Match Teaser screen.
 *
 * Disapprove -> the match is quietly cancelled. Deliberately no
 * notification to the other person explaining why — same reasoning as the
 * rest of the anonymization work: a decline shouldn't create an awkward
 * "they said no" moment for someone who never even saw a name.
 *
 * Approve, with the other side already approved -> revealStatus flips to
 * "revealed" and both devices get a second push ("you're both in") that
 * deep-links to the Radar screen. Approve, other side still pending ->
 * just records it; the caller's own client shows a "waiting" state via its
 * live listener on the match doc, no extra push needed for that case.
 */
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db, messaging } from "./lib/admin";
import type { JudgeFeedDoc, MatchDoc, TwinProfile } from "./types";

interface SubmitMatchApprovalRequest {
  matchId: string;
  approve: boolean;
}

interface SubmitMatchApprovalResponse {
  revealStatus: "pending" | "revealed" | "cancelled";
}

interface ApprovalOutcome extends SubmitMatchApprovalResponse {
  otherId: string;
  /** True only for the call that flipped the match to "revealed", so the push is sent once. */
  justRevealed: boolean;
}

export const submitMatchApproval = onCall<SubmitMatchApprovalRequest>(
  {
    timeoutSeconds: 30,
    memory: "256MiB",
  },
  async (request): Promise<SubmitMatchApprovalResponse> => {
    const { matchId, approve } = request.data ?? {};
    const callerId = request.auth?.uid;

    if (!callerId) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }
    if (!matchId || typeof matchId !== "string") {
      throw new HttpsError("invalid-argument", "matchId is required.");
    }
    if (typeof approve !== "boolean") {
      throw new HttpsError("invalid-argument", "approve must be a boolean.");
    }

    const matchRef = db.collection("matches").doc(matchId);

    // Read and write in one transaction. Both people often tap "I'm in" within
    // the same second; with a plain read-then-write each call saw the other
    // approval as missing, both wrote "pending", and the match never revealed.
    const { revealStatus, otherId, justRevealed } = await db.runTransaction(
      async (tx): Promise<ApprovalOutcome> => {
        const matchSnap = await tx.get(matchRef);
        if (!matchSnap.exists) {
          throw new HttpsError("not-found", "Match not found.");
        }
        const match = matchSnap.data() as MatchDoc;

        if (!match.twinIds.includes(callerId)) {
          throw new HttpsError(
            "permission-denied",
            "Only the two people in this match can respond to it.",
          );
        }
        if (match.status !== "confirmed") {
          throw new HttpsError(
            "failed-precondition",
            "This match was never confirmed — nothing to approve.",
          );
        }
        const otherId = match.twinIds.find((id) => id !== callerId) ?? "";
        if (
          match.revealStatus === "cancelled" ||
          match.revealStatus === "revealed"
        ) {
          // Already settled, idempotent.
          return { revealStatus: match.revealStatus, otherId, justRevealed: false };
        }

        if (!approve) {
          tx.set(matchRef, { revealStatus: "cancelled" }, { merge: true });
          return { revealStatus: "cancelled", otherId, justRevealed: false };
        }

        const updatedApprovals = {
          ...(match.humanApprovals ?? {}),
          [callerId]: "approved" as const,
        };
        const revealed = updatedApprovals[otherId] === "approved";
        tx.set(
          matchRef,
          {
            humanApprovals: updatedApprovals,
            revealStatus: revealed ? "revealed" : "pending",
            // The badges show a match for MATCH_SHOW_MS counted from updatedAt
            // (see lib/box.ts). Without this the window starts at the verdict,
            // so a slow approval leaves them lit for seconds instead of minutes.
            ...(revealed ? { updatedAt: FieldValue.serverTimestamp() } : {}),
          },
          { merge: true },
        );
        return {
          revealStatus: revealed ? "revealed" : "pending",
          otherId,
          justRevealed: revealed,
        };
      },
    );

    if (justRevealed) {
      const [twinA, twinB] = await loadTwins(callerId, otherId);
      // Reveal first: the push opens the radar, which reads the match doc.
      await publishReveal(matchId, [twinA, twinB]);
      await notifyBothRevealed(matchId, twinA, twinB);
    }

    return { revealStatus };
  },
);

async function loadTwins(
  twinIdA: string,
  twinIdB: string,
): Promise<[TwinProfile | null, TwinProfile | null]> {
  const [snapA, snapB] = await Promise.all([
    db.collection("twins").doc(twinIdA).get(),
    db.collection("twins").doc(twinIdB).get(),
  ]);
  return [
    snapA.exists ? (snapA.data() as TwinProfile) : null,
    snapB.exists ? (snapB.data() as TwinProfile) : null,
  ];
}

/**
 * Both people have said yes, so the name-redacted copies written at
 * negotiation time (negotiateTwins.ts) are replaced with the real thing: the
 * teaser summaries on the match doc, and the names, photos and transcript on
 * the public judge feed. A failure here must not undo the reveal itself.
 */
async function publishReveal(
  matchId: string,
  twins: ReadonlyArray<TwinProfile | null>,
): Promise<void> {
  try {
    const matchRef = db.collection("matches").doc(matchId);
    const match = (await matchRef.get()).data() as MatchDoc | undefined;
    if (!match) return;

    const summaries = Object.fromEntries(
      twins
        .filter((twin): twin is TwinProfile => twin !== null)
        .map((twin) => [twin.twinId, twin.summary ?? null]),
    );
    const judgeFeed: Partial<JudgeFeedDoc> = {
      names: match.names,
      photoUrls: match.photoUrls,
      transcript: match.transcript,
      revealStatus: "revealed",
      updatedAt: FieldValue.serverTimestamp() as unknown as JudgeFeedDoc["updatedAt"],
    };
    await Promise.all([
      matchRef.set({ summaries }, { merge: true }),
      db.collection("judge_feed").doc(matchId).set(judgeFeed, { merge: true }),
    ]);
  } catch (err) {
    console.error(`Failed to publish reveal for ${matchId}:`, err);
  }
}

async function notifyBothRevealed(
  matchId: string,
  twinA: TwinProfile | null,
  twinB: TwinProfile | null,
): Promise<void> {

  const sendTo = async (recipient: TwinProfile | null, other: TwinProfile | null) => {
    if (!recipient?.fcmTokens?.length) return;
    const otherName = other?.name || "Someone nearby";
    try {
      await messaging.sendEachForMulticast({
        notification: {
          title: "You're both in!",
          body: `${otherName} said yes too — go find each other.`,
        },
        data: {
          type: "twin_match_revealed",
          matchId,
          otherTwinId: other?.twinId ?? "",
          otherName,
          otherPhotoUrl: other?.photoUrl ?? "",
          action: "open_radar",
        },
        tokens: recipient.fcmTokens,
      });
    } catch (err) {
      console.error(`Failed to send reveal notification to ${recipient.twinId}:`, err);
    }
  };

  await Promise.all([sendTo(twinA, twinB), sendTo(twinB, twinA)]);
}
