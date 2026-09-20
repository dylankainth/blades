/**
 * boxState, the single endpoint a Kindred badge polls (about once a second).
 *
 *   GET /boxState?boxId=AB12CD  ->  BoxStateResponse (see types.ts)
 *
 * Unauthenticated on purpose: the badge is an ESP32 with no Firebase Auth, and
 * the response only ever contains first names and a colour. The 6-hex boxId is
 * a weak identifier, not a secret, fine for a hackathon demo, flagged in
 * hardware/box/CONTRACT.md as something to replace before real use.
 *
 * Read-only apart from registering a never-seen badge once, so a 1 Hz poll
 * costs reads but no writes.
 */
import { onRequest } from "firebase-functions/v2/https";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "./lib/admin";
import {
  firstName,
  matchesForTwin,
  otherTwinId,
  pairColour,
  parseBoxId,
  pickLiveMatch,
} from "./lib/box";
import type { BoxDoc, BoxStateResponse, TwinProfile } from "./types";

const UNPAIRED: BoxStateResponse = {
  state: "unpaired",
  ownerName: null,
  otherName: null,
  colorHex: null,
  bleToken: null,
  matchId: null,
};

export const boxState = onRequest(
  { invoker: "public", memory: "256MiB", timeoutSeconds: 15 },
  async (req, res) => {
    res.set("Cache-Control", "no-store");

    if (req.method !== "GET") {
      res.status(405).json({ error: "GET only" });
      return;
    }

    const boxId = parseBoxId(req.query.boxId);
    if (!boxId) {
      res.status(400).json({ error: "boxId must be 6 hex characters" });
      return;
    }

    try {
      res.json(await resolveState(boxId));
    } catch (err) {
      console.error(`boxState failed for ${boxId}:`, err);
      res.status(500).json({ error: "internal" });
    }
  },
);

async function resolveState(boxId: string): Promise<BoxStateResponse> {
  const boxRef = db.collection("boxes").doc(boxId);
  const boxSnap = await boxRef.get();

  if (!boxSnap.exists) {
    // First time this badge has ever phoned home. Registering it is what lets
    // pairBox reject typos ("no such badge online") instead of creating ghosts.
    await boxRef.set({
      boxId,
      twinId: null,
      createdAt: FieldValue.serverTimestamp(),
    });
    return UNPAIRED;
  }

  const box = boxSnap.data() as BoxDoc;
  if (!box.twinId) return UNPAIRED;

  const twinSnap = await db.collection("twins").doc(box.twinId).get();
  const owner = twinSnap.exists ? (twinSnap.data() as TwinProfile) : null;
  const base: BoxStateResponse = {
    ...UNPAIRED,
    state: "idle",
    ownerName: firstName(owner?.name),
    bleToken: box.bleToken ?? null,
  };

  if (box.waveUntil && box.waveUntil.toMillis() > Timestamp.now().toMillis()) {
    return { ...base, state: "paired_wave" };
  }

  const live = pickLiveMatch(await matchesForTwin(box.twinId));
  if (!live) return base;

  const otherId = otherTwinId(live.match, box.twinId);
  return {
    ...base,
    state: live.state,
    otherName: otherId ? firstName(live.match.names?.[otherId]) : null,
    colorHex: pairColour(live.match.matchId),
    matchId: live.match.matchId,
  };
}
