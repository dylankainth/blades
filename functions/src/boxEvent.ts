/**
 * boxEvent, physical input from a badge.
 *
 *   POST /boxEvent  {"boxId":"AB12CD","event":"shake"}  ->  {"ok":true,"met":bool}
 *
 * A shake while a match is on screen is the wearer saying "we actually met".
 * If both people wear badges, both must shake within BUMP_WINDOW_MS of each
 * other (bumping the two badges together does this in one motion). If the
 * other person has no badge, one shake is enough.
 *
 * This is the only place a human confirms anything, and it is a physical
 * gesture between two people standing together, the twins never confirm on
 * anyone's behalf (see CLAUDE.md guardrails).
 */
import { onRequest } from "firebase-functions/v2/https";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "./lib/admin";
import {
  matchesForTwin,
  otherTwinId,
  parseBoxId,
  pickLiveMatch,
} from "./lib/box";
import type { BoxDoc, MatchDoc } from "./types";

const BUMP_WINDOW_MS = 10 * 1000;

export const boxEvent = onRequest(
  { invoker: "public", memory: "256MiB", timeoutSeconds: 15 },
  async (req, res) => {
    res.set("Cache-Control", "no-store");

    if (req.method !== "POST") {
      res.status(405).json({ error: "POST only" });
      return;
    }

    const boxId = parseBoxId(req.body?.boxId);
    if (!boxId || req.body?.event !== "shake") {
      res.status(400).json({ error: 'expected {boxId, event:"shake"}' });
      return;
    }

    try {
      res.json({ ok: true, met: await recordShake(boxId) });
    } catch (err) {
      console.error(`boxEvent failed for ${boxId}:`, err);
      res.status(500).json({ error: "internal" });
    }
  },
);

/** Returns true if this shake completed the "we met" confirmation. */
async function recordShake(boxId: string): Promise<boolean> {
  const boxSnap = await db.collection("boxes").doc(boxId).get();
  const twinId = (boxSnap.data() as BoxDoc | undefined)?.twinId;
  if (!twinId) return false;

  const live = pickLiveMatch(await matchesForTwin(twinId));
  if (!live || live.state !== "match") return false;

  const otherId = otherTwinId(live.match, twinId);
  if (!otherId) return false;

  const otherBoxSnap = await db
    .collection("boxes")
    .where("twinId", "==", otherId)
    .limit(1)
    .get();
  const otherHasBadge = !otherBoxSnap.empty;

  const matchRef = db.collection("matches").doc(live.match.matchId);
  return db.runTransaction(async (tx) => {
    const fresh = (await tx.get(matchRef)).data() as MatchDoc | undefined;
    if (!fresh || fresh.status !== "confirmed" || fresh.metAt) return false;

    const now = Timestamp.now();
    const otherShake = fresh.shakes?.[otherId];
    const bumped =
      !!otherShake && now.toMillis() - otherShake.toMillis() < BUMP_WINDOW_MS;
    const met = !otherHasBadge || bumped;

    tx.update(matchRef, {
      [`shakes.${twinId}`]: now,
      ...(met ? { metAt: now } : {}),
      updatedAt: FieldValue.serverTimestamp(),
    });
    return met;
  });
}
