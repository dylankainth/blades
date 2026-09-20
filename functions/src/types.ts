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
