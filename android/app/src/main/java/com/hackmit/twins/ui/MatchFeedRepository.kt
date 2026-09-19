package com.hackmit.twins.ui

import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/** One row in the Home screen's live negotiation feed. */
data class MatchFeedItem(
    val matchId: String,
    val otherTwinId: String,
    val otherName: String,
    val otherPhotoUrl: String?,
    val reason: String?,
    val status: String, // "confirmed" | "dismissed" | "proposed" | "negotiating"
)

/**
 * Live feed of this twin's recent negotiations — both confirmed matches and
 * dismissed non-matches, per the product decision: non-matches show inline
 * on Home, confirmed matches also show inline but tapping one opens the
 * dedicated MatchScreen (same screen a push notification opens).
 *
 * Reads matches/{matchId} directly using the denormalized names/photoUrls
 * the negotiateTwins Cloud Function writes at negotiation time (see
 * functions/src/negotiateTwins.ts) — no per-item extra Firestore reads.
 */
object MatchFeedRepository {

    private val db get() = Firebase.firestore

    fun listen(
        twinId: String,
        onUpdate: (List<MatchFeedItem>) -> Unit,
    ): ListenerRegistration {
        return db.collection("matches")
            .whereArrayContains("twinIds", twinId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                val items = snapshot.documents.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val twinIds = doc.get("twinIds") as? List<String> ?: return@mapNotNull null
                    val otherTwinId = twinIds.firstOrNull { it != twinId } ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val names = doc.get("names") as? Map<String, String> ?: emptyMap()
                    @Suppress("UNCHECKED_CAST")
                    val photoUrls = doc.get("photoUrls") as? Map<String, String?> ?: emptyMap()
                    MatchFeedItem(
                        matchId = doc.id,
                        otherTwinId = otherTwinId,
                        otherName = names[otherTwinId] ?: "Someone nearby",
                        otherPhotoUrl = photoUrls[otherTwinId],
                        reason = doc.getString("reason"),
                        status = doc.getString("status") ?: "dismissed",
                    )
                }
                onUpdate(items)
            }
    }
}
