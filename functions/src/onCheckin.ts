/**
 * onCheckin — Firestore-triggered Cloud Function (v2).
 *
 * Fires on any write to checkins/{locationId}/people/{twinId}. This is the
 * "fake sensor, real payload" proximity trigger described in CLAUDE.md: a
 * QR/check-in station writes this doc, and that's treated as "this twin is
 * now near this location". On a new check-in, we look up everyone else
 * currently checked in at the same location (within the last ~20 minutes)
 * and kick off pairwise negotiation with each of them.
 *
 * Kept deliberately simple for hackathon scale: no embedding/vector
 * shortlisting here, since a single venue's live check-in list is already
 * small. If the candidate pool grows, shortlist with embeddings before
 * calling runNegotiation (see CLAUDE.md item 2).
 */
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { Timestamp } from "firebase-admin/firestore";
import { db } from "./lib/admin";
import { META_MODEL_API_KEY } from "./lib/secrets";
import { runNegotiation } from "./negotiateTwins";
import type { CheckinEntry } from "./types";

const RECENT_WINDOW_MINUTES = 20;

export const onCheckin = onDocumentWritten(
  {
    document: "checkins/{locationId}/people/{twinId}",
    secrets: [META_MODEL_API_KEY],
    timeoutSeconds: 300,
    memory: "256MiB",
  },
  async (event) => {
    const afterSnap = event.data?.after;
    if (!afterSnap || !afterSnap.exists) {
      // Document deleted — nothing to do.
      return;
    }

    const { locationId, twinId } = event.params as {
      locationId: string;
      twinId: string;
    };

    // The Android client writes `lastSeenAt` (CheckinRepository.kt). It is a
    // serverTimestamp, so it can still be unresolved on the very first
    // trigger — fall back to "now" rather than dropping the event.
    const checkin = afterSnap.data() as CheckinEntry;
    const seenAt = checkin.lastSeenAt ?? Timestamp.now();

    const cutoff = Timestamp.fromMillis(
      seenAt.toMillis() - RECENT_WINDOW_MINUTES * 60 * 1000,
    );

    const peopleRef = db.collection("checkins").doc(locationId).collection("people");
    const recentSnap = await peopleRef.where("lastSeenAt", ">=", cutoff).get();

    const nearbyTwinIds = recentSnap.docs
      .map((doc) => doc.id)
      .filter((id) => id !== twinId);

    if (nearbyTwinIds.length === 0) {
      return;
    }

    const apiKey = META_MODEL_API_KEY.value();

    // Run negotiations in parallel; don't let one failure block the rest.
    const results = await Promise.allSettled(
      nearbyTwinIds.map((otherTwinId) =>
        runNegotiation(twinId, otherTwinId, { locationId, apiKey }),
      ),
    );

    for (const [i, result] of results.entries()) {
      if (result.status === "rejected") {
        console.error(
          `Negotiation failed for ${twinId} <-> ${nearbyTwinIds[i]}:`,
          result.reason,
        );
      }
    }
  },
);
