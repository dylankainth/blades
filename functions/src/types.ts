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
  /** FCM device token(s) to push notifications to. */
  fcmTokens?: string[];
  onboardingComplete?: boolean;
  createdAt?: Timestamp;
  updatedAt?: Timestamp;
}

/** A single chat turn, stored in onboarding_sessions/{twinId}.turns[] */
export interface OnboardingTurn {
  role: "user" | "assistant";
  content: string;
  ts?: Timestamp;
}

/** onboarding_sessions/{twinId} */
export interface OnboardingSession {
  twinId: string;
  turns: OnboardingTurn[];
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
  /** Plain-language one-sentence reason, e.g. "you're both stuck on the same devops problem". */
  reason?: string | null;
  status: MatchStatus;
  /**
   * Denormalized name/photo per twinId, snapshotted at negotiation time —
   * lets a client render "who is this match with" (Home feed, judge
   * dashboard) straight off the match doc instead of an extra twins/{id}
   * read per twin per match.
   */
  names: Record<string, string>;
  photoUrls: Record<string, string | null>;
  createdAt?: Timestamp;
  updatedAt?: Timestamp;
}
