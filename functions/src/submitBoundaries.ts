/**
 * submitBoundaries — callable Cloud Function (v2).
 *
 * Onboarding's "Set your boundaries" step: what this twin is allowed to
 * bring up during negotiation, and what stays off-limits. A lightweight
 * merge write onto twins/{twinId} — see TwinBoundaries in types.ts for the
 * shape and negotiateTwins.ts for how it's actually enforced (it's threaded
 * into the negotiation prompt as an explicit instruction, not a content
 * filter over the extracted profile).
 *
 * Also flips onboardingComplete — this is step 2 of 3 in the onboarding
 * flow (see OnboardingScreen.kt), reached regardless of which/whether any
 * optional sources got connected in step 1. Setting it here (not
 * in submitContext) guarantees a twin that connected zero sources still
 * finishes onboarding once they get this far, same as the old flow's single
 * "Build my twin" submit always did.
 */
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "./lib/admin";

interface SubmitBoundariesRequest {
  twinId: string;
  career: boolean;
  personalInterests: boolean;
  deeplyPersonalHistory: boolean;
}

export const submitBoundaries = onCall<SubmitBoundariesRequest>(
  {
    timeoutSeconds: 30,
    memory: "256MiB",
  },
  async (request): Promise<{ ok: true }> => {
    const { twinId, career, personalInterests, deeplyPersonalHistory } = request.data ?? {};

    if (!twinId || typeof twinId !== "string") {
      throw new HttpsError("invalid-argument", "twinId is required.");
    }
    if (
      typeof career !== "boolean" ||
      typeof personalInterests !== "boolean" ||
      typeof deeplyPersonalHistory !== "boolean"
    ) {
      throw new HttpsError(
        "invalid-argument",
        "career, personalInterests, and deeplyPersonalHistory must all be booleans.",
      );
    }
    if (request.auth && request.auth.uid !== twinId) {
      throw new HttpsError(
        "permission-denied",
        "twinId must match the authenticated caller.",
      );
    }

    await db.collection("twins").doc(twinId).set(
      {
        twinId,
        boundaries: { career, personalInterests, deeplyPersonalHistory },
        onboardingComplete: true,
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );

    return { ok: true };
  },
);
