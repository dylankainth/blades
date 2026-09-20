package com.hackmit.twins.checkin

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Write path for "a twin is present at a location" — called by
 * BleProximityService on real BLE-detected proximity between two phones,
 * using a synthetic per-pair locationId (see the comment there) since BLE
 * proximity has no natural venue to key off of.
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
     * There's no natural venue "location" to key off of — two phones just
     * came near each other, possibly nowhere near a fixed booth — so
     * BleProximityService passes a synthetic, order-independent locationId
     * derived from both twinIds (see the comment there) rather than a real
     * venue id.
     */
    fun recordCheckin(locationId: String, twinId: String, otherTwinId: String? = null) {
        db.collection("checkins")
            .document(locationId)
            .collection("people")
            .document(twinId)
            .set(
                buildMap {
                    put("twinId", twinId)
                    put("lastSeenAt", FieldValue.serverTimestamp())
                    // Who we just detected, when known (BLE). See onCheckin.ts.
                    if (otherTwinId != null) put("otherTwinId", otherTwinId)
                },
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
