package com.hackmit.twins.match

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * At a networking event people have already decided they want to be
 * introduced, so reviewing every match on a phone gets in the way. With this
 * on, the phone gives the user's "I'm in" as soon as their twin confirms a
 * match.
 *
 * It only ever gives this user's yes. The reveal still needs the other
 * person's yes as well, from their own tap or their own networking mode, so
 * nobody is revealed to someone they have not agreed to meet.
 */
object NetworkingMode {

    private const val TAG = "NetworkingMode"
    private const val PREFS = "klick_modes"
    private const val KEY_ON = "networking"

    /** Older matches are history, not someone who just walked in. */
    private const val FRESH_MS = 5 * 60_000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)

    fun set(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_ON, on).apply()

    /** Whether to answer this match for the user. Pure, so it can be tested. */
    fun shouldSayYes(
        on: Boolean,
        status: String?,
        revealStatus: String?,
        myApproval: String?,
        createdAtMs: Long?,
        nowMs: Long,
    ): Boolean =
        on &&
            status == "confirmed" &&
            (revealStatus ?: "pending") == "pending" &&
            myApproval == null &&
            createdAtMs != null &&
            nowMs - createdAtMs <= FRESH_MS

    /**
     * Watches this twin's matches for as long as the returned registration is
     * kept, and says yes to fresh ones while the mode is on. Meant for the
     * BLE foreground service, which is alive whenever the phone is in a pocket.
     */
    fun watch(context: Context, twinId: String, scope: CoroutineScope): ListenerRegistration {
        val appContext = context.applicationContext
        val asked = ConcurrentHashMap.newKeySet<String>()
        return Firebase.firestore.collection("matches")
            .whereArrayContains("twinIds", twinId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) Log.w(TAG, "Match listener failed", error)
                snapshot?.documents.orEmpty().forEach { doc ->
                    val approvals = doc.get("humanApprovals") as? Map<*, *>
                    val wanted = shouldSayYes(
                        on = isOn(appContext),
                        status = doc.getString("status"),
                        revealStatus = doc.getString("revealStatus"),
                        myApproval = approvals?.get(twinId) as? String,
                        createdAtMs = doc.getTimestamp("createdAt")?.toDate()?.time,
                        nowMs = System.currentTimeMillis(),
                    )
                    if (wanted && asked.add(doc.id)) scope.sayYes(doc.id, asked)
                }
            }
    }

    private fun CoroutineScope.sayYes(matchId: String, asked: MutableSet<String>) = launch {
        try {
            val reveal = MatchDetailRepository.submitApproval(matchId, approve = true)
            Log.i(TAG, "Said yes to $matchId for the user; reveal is $reveal")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Forget it so the next snapshot tries again.
            asked.remove(matchId)
            Log.e(TAG, "Could not say yes to $matchId", e)
        }
    }
}
