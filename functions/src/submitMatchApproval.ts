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
import { db, messaging } from "./lib/admin";
import type { MatchDoc, TwinProfile } from "./types";

interface SubmitMatchApprovalRequest {
  matchId: string;
  approve: boolean;
}

interface SubmitMatchApprovalResponse {
  revealStatus: "pending" | "revealed" | "cancelled";
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
    const matchSnap = await matchRef.get();
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
    if (match.revealStatus === "cancelled") {
      return { revealStatus: "cancelled" };
    }
    if (match.revealStatus === "revealed") {
      return { revealStatus: "revealed" }; // already done, idempotent
    }

    if (!approve) {
      await matchRef.set(
        { revealStatus: "cancelled" },
        { merge: true },
      );
      return { revealStatus: "cancelled" };
    }

    const otherId = match.twinIds.find((id) => id !== callerId) ?? "";
    const updatedApprovals = {
      ...(match.humanApprovals ?? {}),
      [callerId]: "approved" as const,
    };
    const otherApproved = updatedApprovals[otherId] === "approved";
    const revealStatus = otherApproved ? "revealed" : "pending";

    await matchRef.set(
      { humanApprovals: updatedApprovals, revealStatus },
      { merge: true },
    );

    if (revealStatus === "revealed") {
      await notifyBothRevealed(match.matchId, callerId, otherId);
    }

    return { revealStatus };
  },
);

async function notifyBothRevealed(
  matchId: string,
  twinIdA: string,
  twinIdB: string,
): Promise<void> {
  const [snapA, snapB] = await Promise.all([
    db.collection("twins").doc(twinIdA).get(),
    db.collection("twins").doc(twinIdB).get(),
  ]);
  const twinA = snapA.exists ? (snapA.data() as TwinProfile) : null;
  const twinB = snapB.exists ? (snapB.data() as TwinProfile) : null;

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
