package com.hackmit.twins.checkin

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Single write path for "a twin is present at a location," shared by both
 * proximity triggers:
 *  - BleProximityService, for real BLE-detected proximity between two phones.
 *  - CheckinScreen, for the manual "I'm at booth X" demo-day fallback.
 *
 * Firestore layout: checkins/{locationId}/people/{twinId}
 * A Cloud Function (not part of this Android scaffold) watches this
 * collection group and runs the matching engine whenever two twins land in
 * the same locationId within a short time window.
 */
object CheckinRepository {

    private val db get() = Firebase.firestore

    /**
     * Records that [twinId] was just seen at [locationId].
     *
     * For BLE proximity there's no natural venue "location" to key off of —
     * two phones just came near each other, possibly nowhere near a fixed
     * booth — so BleProximityService passes a synthetic locationId (see the
     * comment there) rather than a real venue id. Manual check-ins use a
     * real, human-chosen locationId like "booth-3".
     */
    fun recordCheckin(locationId: String, twinId: String) {
        db.collection("checkins")
            .document(locationId)
            .collection("people")
            .document(twinId)
            .set(
                mapOf(
                    "twinId" to twinId,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                ),
                // Merge so repeated detections just bump lastSeenAt instead
                // of erroring/overwriting other fields a Cloud Function adds.
                com.google.firebase.firestore.SetOptions.merge(),
            )
        // Fire-and-forget: this is a best-effort presence signal. If it's
        // offline, Firestore's local cache will queue and retry the write
        // once connectivity returns, which is good enough for a hackathon
        // demo — we don't block the caller waiting for a Task to complete.
    }
}
