package com.hackmit.twins.match

import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Live view of one confirmed match — everything the Match Teaser and Radar
 * screens need, read straight off matches/{matchId} (denormalized at
 * negotiation time — see functions/src/negotiateTwins.ts — since
 * twins/{twinId} is owner-only and the app never reads another twin's
 * profile directly).
 */
data class MatchLiveDetail(
    val matchId: String,
    val otherTwinId: String,
    val otherName: String,
    val otherPhotoUrl: String?,
    val reason: String?,
    val score: Int?,
    val summary: String?,
    val interests: List<String>,
    /** "pending" until both approve, "revealed" once both have, "cancelled" if either declined. */
    val revealStatus: String,
    val myApproval: String?,
    val otherApproval: String?,
)

object MatchDetailRepository {

    private val db get() = Firebase.firestore

    fun listen(
        matchId: String,
        myTwinId: String,
        onUpdate: (MatchLiveDetail?) -> Unit,
    ): ListenerRegistration {
        return db.collection("matches").document(matchId)
            .addSnapshotListener { doc, _ ->
                if (doc == null || !doc.exists()) {
                    onUpdate(null)
                    return@addSnapshotListener
                }

                @Suppress("UNCHECKED_CAST")
                val twinIds = doc.get("twinIds") as? List<String> ?: return@addSnapshotListener
                val otherTwinId = twinIds.firstOrNull { it != myTwinId } ?: return@addSnapshotListener
                @Suppress("UNCHECKED_CAST")
                val names = doc.get("names") as? Map<String, String> ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val photoUrls = doc.get("photoUrls") as? Map<String, String?> ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val summaries = doc.get("summaries") as? Map<String, String?> ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val interestsByTwin = doc.get("interestsByTwin") as? Map<String, List<String>> ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val approvals = doc.get("humanApprovals") as? Map<String, String> ?: emptyMap()

                onUpdate(
                    MatchLiveDetail(
                        matchId = matchId,
                        otherTwinId = otherTwinId,
                        otherName = names[otherTwinId] ?: "Someone nearby",
                        otherPhotoUrl = photoUrls[otherTwinId],
                        reason = doc.getString("reason"),
                        score = doc.getLong("score")?.toInt(),
                        summary = summaries[otherTwinId],
                        interests = interestsByTwin[otherTwinId] ?: emptyList(),
                        revealStatus = doc.getString("revealStatus") ?: "pending",
                        myApproval = approvals[myTwinId],
                        otherApproval = approvals[otherTwinId],
                    ),
                )
            }
    }

    /** Returns the new revealStatus ("pending" | "revealed" | "cancelled"). */
    suspend fun submitApproval(matchId: String, approve: Boolean): String {
        val payload = hashMapOf("matchId" to matchId, "approve" to approve)
        val result = Firebase.functions
            .getHttpsCallable("submitMatchApproval")
            .call(payload)
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?>
        return data?.get("revealStatus") as? String ?: "pending"
    }
}
