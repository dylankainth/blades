package com.hackmit.twins.ble

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Maps a short random BLE-advertised session token back to the real
 * twinId that generated it — see BleProximityService.kt for why this
 * indirection exists (legacy BLE advertisement packets cap at 31 bytes,
 * nowhere near enough to fit a raw Firebase uid).
 */
object BleSessionRepository {

    private val db get() = Firebase.firestore

    fun registerToken(tokenHex: String, twinId: String) {
        db.collection("ble_sessions").document(tokenHex).set(
            mapOf(
                "twinId" to twinId,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        // Fire-and-forget, same reasoning as CheckinRepository: best-effort,
        // Firestore's offline queue handles retry if this races connectivity.
    }

    suspend fun resolveToken(tokenHex: String): String? {
        val snap = db.collection("ble_sessions").document(tokenHex).get().await()
        return snap.getString("twinId")
    }
}
