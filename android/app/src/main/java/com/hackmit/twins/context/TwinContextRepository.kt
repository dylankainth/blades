package com.hackmit.twins.context

import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * One categorized, bite-sized observation the twin's context extraction
 * pulled out — mirrors functions/src/types.ts's TwinFact exactly, since
 * this is read straight off twins/{twinId}.facts.
 */
data class TwinFact(
    val category: String = "",
    val detail: String = "",
    val edited: Boolean = false,
)

/**
 * Which optional context sources have contributed something — derived from
 * the same `[[provider]]\n...` markers importSocialContext.ts writes into
 * socialContext (see its parseSocialSections/serializeSocialSections), so
 * this never drifts from what the server considers "connected".
 */
data class TwinConnections(
    val context: Boolean = false,
    val instagram: Boolean = false,
    val linkedin: Boolean = false,
)

data class TwinContextSnapshot(
    val facts: List<TwinFact>,
    val interests: List<String>,
    val connections: TwinConnections,
)

/**
 * Backs the "Everything it knows" screen (TwinContextScreen.kt) — reads
 * twins/{twinId} directly, same owner-only doc PushTokenRepository writes
 * to, and writes fact edits back the same way (a merged client write, no
 * Cloud Function needed since firestore.rules already allows the owner
 * full read/write on their own twin doc).
 */
object TwinContextRepository {

    private val db get() = Firebase.firestore

    fun listen(twinId: String, onUpdate: (TwinContextSnapshot) -> Unit): ListenerRegistration {
        return db.collection("twins").document(twinId)
            .addSnapshotListener { doc, _ ->
                if (doc == null || !doc.exists()) {
                    onUpdate(TwinContextSnapshot(emptyList(), emptyList(), TwinConnections()))
                    return@addSnapshotListener
                }

                @Suppress("UNCHECKED_CAST")
                val rawFacts = doc.get("facts") as? List<Map<String, Any?>> ?: emptyList()
                val facts = rawFacts.mapNotNull { f ->
                    val category = f["category"] as? String ?: return@mapNotNull null
                    val detail = f["detail"] as? String ?: return@mapNotNull null
                    TwinFact(category = category, detail = detail, edited = f["edited"] as? Boolean ?: false)
                }

                @Suppress("UNCHECKED_CAST")
                val interests = doc.get("interests") as? List<String> ?: emptyList()

                val socialContext = doc.getString("socialContext")
                val connections = TwinConnections(
                    context = !doc.getString("rawContext").isNullOrBlank(),
                    instagram = socialContext?.contains("[[instagram]]") == true,
                    linkedin = socialContext?.contains("[[linkedin]]") == true,
                )

                onUpdate(TwinContextSnapshot(facts = facts, interests = interests, connections = connections))
            }
    }

    /**
     * Overwrites the whole facts array with [facts] — Firestore has no
     * "update array element at index" primitive, so any single edit/delete
     * round-trips the full (short, ~5-6 item) list. Callers should mark an
     * edited entry's `edited = true` so the next re-extraction (see
     * mergeFacts in functions/src/lib/extractProfile.ts) preserves it
     * instead of silently overwriting it with a fresh guess.
     */
    suspend fun saveFacts(twinId: String, facts: List<TwinFact>) {
        val payload = facts.map { f ->
            mapOf("category" to f.category, "detail" to f.detail, "edited" to f.edited)
        }
        db.collection("twins").document(twinId)
            .set(mapOf("facts" to payload), SetOptions.merge())
            .await()
    }
}
