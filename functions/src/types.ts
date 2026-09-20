import type { Timestamp } from "firebase-admin/firestore";

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
  locationId: string;
  checkedInAt: Timestamp;
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
