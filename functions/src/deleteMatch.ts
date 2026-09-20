/**
 * deleteMatch — callable Cloud Function (v2).
 *
 * Lets a participant remove one entry from their own Recent Searches feed
 * (RecentSearchesScreen.kt) — swipe-to-delete on a confirmed match or a
 * dismissed non-match. matches/{matchId} write is always false in
 * firestore.rules (server-only), so this has to go through a Cloud
 * Function rather than a direct client delete.
 *
 * Deletes both matches/{matchId} (the participant-only doc the feed reads)
 * and judge_feed/{matchId} (the public-safe copy) together, so a removed
 * entry disappears from the judge dashboard too rather than leaving an
 * orphaned public record for a negotiation the participant chose to clear.
 *
 * One-sided: either participant can delete their own copy of the
 * conversation. There's no "restore" — this is a permanent removal, same
 * as clearing any other local history list.
 */
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { db } from "./lib/admin";
import type { MatchDoc } from "./types";

interface DeleteMatchRequest {
  matchId: string;
}

export const deleteMatch = onCall<DeleteMatchRequest>(
  {
    timeoutSeconds: 30,
    memory: "256MiB",
  },
  async (request): Promise<{ ok: true }> => {
    const { matchId } = request.data ?? {};
    const callerId = request.auth?.uid;

    if (!callerId) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }
    if (!matchId || typeof matchId !== "string") {
      throw new HttpsError("invalid-argument", "matchId is required.");
    }

    const matchRef = db.collection("matches").doc(matchId);
    const matchSnap = await matchRef.get();
    if (!matchSnap.exists) {
      // Already gone — deleting a nonexistent doc is a no-op, and the
      // client's optimistic removal already reflects this either way.
      return { ok: true };
    }
    const match = matchSnap.data() as MatchDoc;
    if (!match.twinIds.includes(callerId)) {
      throw new HttpsError(
        "permission-denied",
        "Only the two people in this match can delete it.",
      );
    }

    const batch = db.batch();
    batch.delete(matchRef);
    batch.delete(db.collection("judge_feed").doc(matchId));
    await batch.commit();

    return { ok: true };
  },
);
