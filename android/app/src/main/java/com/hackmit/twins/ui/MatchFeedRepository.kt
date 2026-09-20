package com.hackmit.twins.ui

import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/** One row in the Home screen's live negotiation feed. */
data class MatchFeedItem(
    val matchId: String,
    val otherTwinId: String,
    val otherName: String,
    val otherPhotoUrl: String?,
    val reason: String?,
    val score: Int?,
    val status: String, // "confirmed" | "dismissed" | "proposed" | "negotiating"
    val revealStatus: String?, // "pending" | "revealed" | "cancelled" — only set once status == "confirmed"
)

/** One line of the negotiation transcript, ready to render as a chat bubble. */
data class NegotiationTurnUi(val fromMe: Boolean, val text: String)

/** Full detail for the "why weren't we a match" screen. */
data class NegotiationDetail(
    val matchId: String,
    val otherName: String,
    val transcript: List<NegotiationTurnUi>,
    val score: Int?,
    val reason: String?,
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
                        score = (doc.getLong("score"))?.toInt(),
                        status = doc.getString("status") ?: "dismissed",
                        revealStatus = doc.getString("revealStatus"),
                    )
                }
                onUpdate(items)
            }
    }

    /**
     * One-shot fetch of the full negotiation (transcript included) for the
     * detail screen — deliberately not part of the live list query above,
     * which stays lightweight since it renders every recent item at once.
     */
    suspend fun fetchDetail(matchId: String, myTwinId: String): NegotiationDetail? {
        val doc = db.collection("matches").document(matchId).get().await()
        if (!doc.exists()) return null

        @Suppress("UNCHECKED_CAST")
        val twinIds = doc.get("twinIds") as? List<String> ?: return null
        val otherTwinId = twinIds.firstOrNull { it != myTwinId }
        @Suppress("UNCHECKED_CAST")
        val names = doc.get("names") as? Map<String, String> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val rawTranscript = doc.get("transcript") as? List<Map<String, Any?>> ?: emptyList()

        return NegotiationDetail(
            matchId = matchId,
            otherName = names[otherTwinId] ?: "Someone nearby",
            transcript = rawTranscript.mapNotNull { turn ->
                val speakerTwinId = turn["speakerTwinId"] as? String ?: return@mapNotNull null
                val content = turn["content"] as? String ?: return@mapNotNull null
                NegotiationTurnUi(fromMe = speakerTwinId == myTwinId, text = content)
            },
            score = (doc.getLong("score"))?.toInt(),
            reason = doc.getString("reason"),
        )
    }

    /**
     * Removes one entry from Recent Searches — swipe-to-delete. Goes
     * through the deleteMatch callable rather than a direct client delete
     * since matches/{matchId} write is always false in firestore.rules
     * (server-only). Deletes the shared doc outright, so this removes the
     * entry for both participants, not just the caller's own view.
     */
    suspend fun deleteMatch(matchId: String) {
        Firebase.functions
            .getHttpsCallable("deleteMatch")
            .call(hashMapOf("matchId" to matchId))
            .await()
    }
}
