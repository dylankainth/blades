/**
 * Shared helpers for the Kindred badge (ESP32-S3-BOX-3) functions.
 *
 * A badge is a dumb terminal: it polls boxState, renders whatever state it is
 * told, and reports shakes to boxEvent. Everything it shows is derived here
 * from the same matches/{matchId} docs the phone app and judge dashboard use,
 * so there is no second source of truth to keep in sync.
 */
import type { Timestamp } from "firebase-admin/firestore";
import { db } from "./admin";
import type { BoxState, MatchDoc } from "../types";

const BOX_ID_PATTERN = /^[0-9A-F]{6}$/;
const QR_PREFIX = "kindred-box:";

/** How long each transient state stays on the badge. */
export const NEGOTIATING_SHOW_MS = 90 * 1000;
export const MATCH_SHOW_MS = 5 * 60 * 1000;
export const NO_MATCH_SHOW_MS = 8 * 1000;
export const MET_SHOW_MS = 20 * 1000;

/**
 * Pair colours. Saturated and far apart in hue so two badges showing the same
 * one are obviously "together" from across a table, and two different pairs
 * standing next to each other are obviously not.
 */
const PAIR_COLOURS = [
  "#FF6B35", // orange
  "#2EC4B6", // teal
  "#E71D73", // magenta
  "#FFD23F", // yellow
  "#7B2FF7", // violet
  "#3DDC84", // green
  "#00A8FF", // sky
  "#FF4D6D", // coral
];

/** Accepts "AB12CD" or the QR payload "kindred-box:AB12CD". Returns null if invalid. */
export function parseBoxId(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const trimmed = raw.trim();
  const id = (
    trimmed.toLowerCase().startsWith(QR_PREFIX)
      ? trimmed.slice(QR_PREFIX.length)
      : trimmed
  ).toUpperCase();
  return BOX_ID_PATTERN.test(id) ? id : null;
}

export function pairColour(matchId: string): string {
  let hash = 0;
  for (const char of matchId) {
    hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  }
  return PAIR_COLOURS[hash % PAIR_COLOURS.length];
}

export function firstName(fullName: string | null | undefined): string | null {
  const first = fullName?.trim().split(/\s+/)[0];
  return first ? first : null;
}

export interface LiveMatch {
  state: BoxState;
  match: MatchDoc;
}

function ageMs(ts: Timestamp | null | undefined, now: number): number {
  return ts ? now - ts.toMillis() : Number.POSITIVE_INFINITY;
}

/**
 * Picks the one thing a twin's badge should be showing, or null for idle.
 *
 * Priority is deliberate: a celebration beats a greeting, a greeting is never
 * interrupted by some other pair's negotiation starting, and "no match" is
 * only a brief shrug.
 */
export function pickLiveMatch(
  matches: MatchDoc[],
  now: number = Date.now(),
): LiveMatch | null {
  const newestFirst = [...matches].sort(
    (a, b) => ageMs(a.updatedAt, now) - ageMs(b.updatedAt, now),
  );

  const met = newestFirst.find(
    (m) => m.status === "confirmed" && ageMs(m.metAt, now) < MET_SHOW_MS,
  );
  if (met) return { state: "met", match: met };

  // A badge faces strangers, so it only names the other person once BOTH
  // humans approved the reveal (submitMatchApproval.ts). Docs written before
  // that flow existed have no revealStatus and count as revealed.
  const greeting = newestFirst.find(
    (m) =>
      m.status === "confirmed" &&
      (m.revealStatus ?? "revealed") === "revealed" &&
      !m.metAt &&
      ageMs(m.updatedAt, now) < MATCH_SHOW_MS,
  );
  if (greeting) return { state: "match", match: greeting };

  // Twins still talking, or twins agreed and the humans are deciding on their
  // phones: either way the badge just looks busy and gives nothing away. A
  // cancelled reveal falls through to idle, quietly, by design.
  const negotiating = newestFirst.find(
    (m) =>
      (m.status === "negotiating" &&
        ageMs(m.updatedAt, now) < NEGOTIATING_SHOW_MS) ||
      (m.status === "confirmed" &&
        m.revealStatus === "pending" &&
        ageMs(m.updatedAt, now) < MATCH_SHOW_MS),
  );
  if (negotiating) return { state: "negotiating", match: negotiating };

  const shrug = newestFirst.find(
    (m) =>
      m.status === "dismissed" && ageMs(m.updatedAt, now) < NO_MATCH_SHOW_MS,
  );
  if (shrug) return { state: "no_match", match: shrug };

  return null;
}

/** Every match doc this twin is part of. Hackathon scale: tens, not thousands. */
export async function matchesForTwin(twinId: string): Promise<MatchDoc[]> {
  const snap = await db
    .collection("matches")
    .where("twinIds", "array-contains", twinId)
    .get();
  return snap.docs.map((doc) => doc.data() as MatchDoc);
}

export function otherTwinId(match: MatchDoc, twinId: string): string | null {
  return match.twinIds.find((id) => id !== twinId) ?? null;
}
