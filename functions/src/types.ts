import type { Timestamp } from "firebase-admin/firestore";

/** twins/{twinId}.boundaries — see TwinProfile.boundaries and submitBoundaries.ts. */
export interface TwinBoundaries {
  career: boolean;
  personalInterests: boolean;
  deeplyPersonalHistory: boolean;
}

export const DEFAULT_BOUNDARIES: TwinBoundaries = {
  career: true,
  personalInterests: true,
  deeplyPersonalHistory: false,
};

/** twins/{twinId} */
export interface TwinProfile {
  twinId: string;
  name: string;
  photoUrl?: string | null;
  /** Rolling plain-language summary Claude maintains of who this person is. */
  summary?: string | null;
  /** Short list of extracted interests/tags, used for shortlisting. */
  interests?: string[];
  /**
   * The user's own pasted "tell us about yourself" text dump — kept
   * verbatim for provenance/debugging. This is the primary (Tier A) source
   * `summary`/`interests` are extracted from. See submitContext.ts.
   */
  rawContext?: string | null;
  /**
   * Aggregated caption/post text pulled from Instagram/Facebook via
   * Graph API (Tier B — tester/role accounts only, see
   * importSocialContext.ts). Kept separate from `rawContext` since it's
   * machine-scraped rather than user-authored; merged alongside it when
   * re-running extraction. Null/absent for the vast majority of users who
   * aren't on the Meta App's tester list.
   */
  socialContext?: string | null;
  /** FCM device token(s) to push notifications to. */
  fcmTokens?: string[];
  /**
   * What this twin is allowed to bring up during negotiation — the
   * onboarding "Set your boundaries" step. Defaults (career: true,
   * personalInterests: true, deeplyPersonalHistory: false) apply whenever
   * this is absent, so older twins created before this field existed
   * behave the same as someone who accepted the defaults. Read by
   * negotiateTwins.ts to instruct the model what NOT to surface about this
   * person, even if it's present in their summary/interests/rawContext.
   */
  boundaries?: TwinBoundaries;
  onboardingComplete?: boolean;
  createdAt?: Timestamp;
  updatedAt?: Timestamp;
}

/**
 * context_submissions/{twinId} — audit trail of what a twin's context was
 * actually built from (the raw text dump + any scraped social text). Not
 * read by negotiateTwins.ts; twins/{twinId}'s `summary`/`interests` are the
 * derived fields that actually drive matching. This exists purely so a
 * human can later see/debug what a given twin's persona was sourced from.
 */
export interface ContextSubmission {
  twinId: string;
  textDump: string;
  socialContext?: string | null;
  createdAt?: Timestamp;
  updatedAt?: Timestamp;
}

/** checkins/{locationId}/people/{twinId} */
export interface CheckinEntry {
  twinId: string;
  locationId?: string;
  /** Written by the Android client (CheckinRepository.kt) on every detection. */
  lastSeenAt?: Timestamp;
  /** Set on BLE detections: the twin this device just saw nearby. */
  otherTwinId?: string;
}

export type MatchStatus =
  | "proposed"
  | "negotiating"
  | "confirmed"
  | "dismissed";

/**
 * Human approval, layered on top of the AI's "confirmed" — a second,
 * explicit consent gate before either person's real identity is revealed
 * to the other or the BLE radar unlocks. See submitMatchApproval.ts.
 */
export type RevealStatus = "pending" | "revealed" | "cancelled";

/** One turn of the twin-to-twin negotiation transcript. */
export interface NegotiationTurn {
  speakerTwinId: string;
  content: string;
  ts?: Timestamp;
}

/** matches/{matchId} */
export interface MatchDoc {
  matchId: string;
  twinIds: [string, string];
  locationId?: string | null;
  transcript: NegotiationTurn[];
  /** Plain-language one-sentence reason — kept whether it's a match or not. */
  reason?: string | null;
  /** 0-100 alignment score from the negotiation's final convergence step. */
  score?: number | null;
  status: MatchStatus;
  /**
   * Denormalized name/photo per twinId, snapshotted at negotiation time —
   * lets a client render "who is this match with" (Home feed, judge
   * dashboard) straight off the match doc instead of an extra twins/{id}
   * read per twin per match.
   */
  names: Record<string, string>;
  photoUrls: Record<string, string | null>;
  /** Per-twin timestamp of the last badge shake/bump while this match was live. */
  shakes?: Record<string, Timestamp>;
  /** Set once both people confirmed they met (see boxEvent.ts). */
  metAt?: Timestamp | null;
  /**
   * Denormalized "key facts" for the Match Teaser screen — same reasoning
   * as names/photoUrls above: twins/{twinId} is owner-only, so anything
   * the OTHER person's client needs to render has to be snapshotted here
   * at negotiation time. Only written when the match is confirmed (a
   * dismissed pairing has no teaser screen to feed).
   */
  summaries?: Record<string, string | null>;
  interestsByTwin?: Record<string, string[]>;
  /** Per-twin human approval of the reveal — see RevealStatus. */
  humanApprovals?: Record<string, "approved" | "declined">;
  revealStatus?: RevealStatus;
  createdAt?: Timestamp;
  updatedAt?: Timestamp;
}

/**
 * judge_feed/{matchId} — the public-safe copy of a negotiation, readable by
 * any signed-in user (unlike matches/{matchId}, which is participant-only).
 * For a confirmed match this mirrors the real data — that's the "wow"
 * moment judges should see. For a dismissed one it's deliberately just the
 * outcome: no names, no photos, no reason text, no transcript, since the
 * reason/transcript tend to mention real names in the model's own words —
 * a simple redacted names map wouldn't catch that. See negotiateTwins.ts.
 */
export interface JudgeFeedDoc {
  matchId: string;
  status: MatchStatus;
  score?: number | null;
  reason?: string | null;
  transcript?: NegotiationTurn[];
  names?: Record<string, string>;
  photoUrls?: Record<string, string | null>;
  createdAt?: Timestamp;
  updatedAt?: Timestamp;
}

/** What a Kindred badge (ESP32-S3-BOX-3) should be showing right now. */
export type BoxState =
  | "unpaired"
  | "paired_wave"
  | "idle"
  | "negotiating"
  | "match"
  | "no_match"
  | "met";

/** boxes/{boxId} — one physical badge. Only Cloud Functions touch this. */
export interface BoxDoc {
  boxId: string;
  twinId: string | null;
  /** 8-byte hex token the badge advertises over BLE on its owner's behalf. */
  bleToken?: string | null;
  pairedAt?: Timestamp;
  /** The badge plays its hello wave until this moment. */
  waveUntil?: Timestamp;
  lastPolledAt?: Timestamp;
  lastShakeAt?: Timestamp;
}

/** JSON body returned by GET boxState — the whole firmware contract. */
export interface BoxStateResponse {
  state: BoxState;
  ownerName: string | null;
  otherName: string | null;
  colorHex: string | null;
  bleToken: string | null;
  matchId: string | null;
}
