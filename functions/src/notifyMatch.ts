/**
 * notifyMatch — Firestore-triggered Cloud Function (v2).
 *
 * Fires on writes to matches/{matchId}. When a match's status transitions
 * to "confirmed" (meaning the negotiation engine decided this pair is worth
 * surfacing — see negotiateTwins.ts), sends an FCM push notification to
 * both twins' devices with the other person's name/photo and the one
 * sentence plain-language reason to talk.
 *
 * GUARDRAIL (per CLAUDE.md): this is a "confirm / say hi" prompt only. It
 * never sends a message to the other person and never books/schedules
 * anything on the human's behalf — the notification's only action is
 * handing off to a real human interaction.
 */
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { db, messaging } from "./lib/admin";
import type { MatchDoc, TwinProfile } from "./types";

export const notifyMatch = onDocumentWritten(
  {
    document: "matches/{matchId}",
    timeoutSeconds: 60,
    memory: "256MiB",
  },
  async (event) => {
    const before = event.data?.before?.exists
      ? (event.data.before.data() as MatchDoc)
      : null;
    const after = event.data?.after?.exists
      ? (event.data.after.data() as MatchDoc)
      : null;

    if (!after) return; // deleted
    if (after.status !== "confirmed") return;
    if (before?.status === "confirmed") return; // already notified

    const [twinIdA, twinIdB] = after.twinIds;
    const [twinASnap, twinBSnap] = await Promise.all([
      db.collection("twins").doc(twinIdA).get(),
      db.collection("twins").doc(twinIdB).get(),
    ]);

    const twinA = twinASnap.exists ? (twinASnap.data() as TwinProfile) : null;
    const twinB = twinBSnap.exists ? (twinBSnap.data() as TwinProfile) : null;

    const reason = after.reason ?? "Your twins think you two should talk.";

    const sendTo = async (recipient: TwinProfile | null, other: TwinProfile | null) => {
      if (!recipient?.fcmTokens?.length) return;

      const otherName = other?.name || "someone nearby";
      const body = `${otherName}'s twin and I think you two should talk — ${reason}`;

      const message = {
        notification: {
          title: `Say hi to ${otherName}?`,
          body,
          ...(other?.photoUrl ? { imageUrl: other.photoUrl } : {}),
        },
        data: {
          type: "twin_match",
          matchId: after.matchId,
          otherTwinId: other?.twinId ?? "",
          otherName: otherName,
          otherPhotoUrl: other?.photoUrl ?? "",
          reason,
          // Client renders a single tap action ("Say hi") that opens a
          // human-to-human interaction. It never auto-sends a message or
          // auto-books anything — see CLAUDE.md guardrails.
          action: "confirm_say_hi",
        },
        tokens: recipient.fcmTokens,
      };

      try {
        await messaging.sendEachForMulticast(message);
      } catch (err) {
        console.error(`Failed to send notification to ${recipient.twinId}:`, err);
      }
    };

    await Promise.all([sendTo(twinA, twinB), sendTo(twinB, twinA)]);
  },
);
