/**
 * pairBox / resetDemo, the two badge-related callables the Android app uses.
 *
 * pairBox: the app scans the QR on a badge ("kindred-box:AB12CD") and calls
 * this. The badge is bound to the caller's twin, plays its hello wave, and
 * gets its own BLE session token so it can announce its owner's presence even
 * when the owner's phone is not advertising.
 *
 * resetDemo: expo judging means running the same demo for judge after judge.
 * Matches are keyed by pair and only negotiated once, so without a reset the
 * second judge sees nothing happen.
 */
import { randomBytes } from "node:crypto";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "./lib/admin";
import { firstName, matchesForTwin, parseBoxId } from "./lib/box";
import type { TwinProfile } from "./types";

const WAVE_MS = 6 * 1000;
const BLE_TOKEN_BYTES = 8;

interface PairBoxRequest {
  boxId: string;
}

interface PairBoxResult {
  boxId: string;
  ownerName: string | null;
}

export const pairBox = onCall<PairBoxRequest, Promise<PairBoxResult>>(
  { memory: "256MiB", timeoutSeconds: 30 },
  async (request) => {
    const twinId = request.auth?.uid;
    if (!twinId) {
      throw new HttpsError("unauthenticated", "Sign in before pairing a badge.");
    }

    const boxId = parseBoxId(request.data?.boxId);
    if (!boxId) {
      throw new HttpsError("invalid-argument", "That is not a Kindred badge code.");
    }

    const boxRef = db.collection("boxes").doc(boxId);
    const [boxSnap, twinSnap, previousSnap] = await Promise.all([
      boxRef.get(),
      db.collection("twins").doc(twinId).get(),
      db.collection("boxes").where("twinId", "==", twinId).get(),
    ]);

    if (!boxSnap.exists) {
      throw new HttpsError(
        "not-found",
        "That badge has not come online yet. Check it is powered and on Wi-Fi.",
      );
    }

    const bleToken = randomBytes(BLE_TOKEN_BYTES).toString("hex");
    const batch = db.batch();

    // One badge per person: re-pairing moves you, it does not clone you.
    for (const doc of previousSnap.docs) {
      if (doc.id !== boxId) {
        batch.update(doc.ref, { twinId: null, bleToken: null });
      }
    }
    batch.set(db.collection("ble_sessions").doc(bleToken), {
      twinId,
      source: "box",
      boxId,
      createdAt: FieldValue.serverTimestamp(),
    });
    batch.set(
      boxRef,
      {
        boxId,
        twinId,
        bleToken,
        pairedAt: FieldValue.serverTimestamp(),
        waveUntil: Timestamp.fromMillis(Date.now() + WAVE_MS),
      },
      { merge: true },
    );
    await batch.commit();

    const owner = twinSnap.exists ? (twinSnap.data() as TwinProfile) : null;
    return { boxId, ownerName: firstName(owner?.name) };
  },
);

interface ResetDemoResult {
  deletedMatches: number;
}

/** Order-independent id the Android client uses for a BLE pair's check-in bucket. */
function pairLocationId(a: string, b: string): string {
  const [first, second] = [a, b].sort();
  return `ble-pair-${first.slice(0, 8)}-${second.slice(0, 8)}`;
}

export const resetDemo = onCall<unknown, Promise<ResetDemoResult>>(
  { memory: "256MiB", timeoutSeconds: 60 },
  async (request) => {
    const twinId = request.auth?.uid;
    if (!twinId) {
      throw new HttpsError("unauthenticated", "Sign in before resetting.");
    }

    const matches = await matchesForTwin(twinId);
    const batch = db.batch();
    for (const match of matches) {
      batch.delete(db.collection("matches").doc(match.matchId));
      const [a, b] = match.twinIds;
      const people = db
        .collection("checkins")
        .doc(pairLocationId(a, b))
        .collection("people");
      batch.delete(people.doc(a));
      batch.delete(people.doc(b));
    }
    await batch.commit();

    return { deletedMatches: matches.length };
  },
);
