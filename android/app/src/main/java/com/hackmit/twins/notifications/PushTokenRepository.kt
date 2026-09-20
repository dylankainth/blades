package com.hackmit.twins.notifications

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.hackmit.twins.auth.AuthManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Keeps this device's FCM token on the signed-in twin's profile
 * (twins/{twinId}.fcmTokens), which is where notifyMatch.ts looks to deliver
 * "your twin found someone". Without it no match push can reach the phone,
 * and the earbud whisper that rides on that push never fires either.
 *
 * Two entry points because either half can arrive first: the token can be
 * minted before anyone has signed in (onNewToken), and sign-in can happen
 * long after the token exists (syncCurrentToken).
 */
object PushTokenRepository {

    private const val TAG = "PushToken"

    /** Call once a twin is signed in. Safe to call repeatedly. */
    suspend fun syncCurrentToken() {
        try {
            register(FirebaseMessaging.getInstance().token.await())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't fetch the FCM token; match pushes won't arrive", e)
        }
    }

    /** Stores [token] for the signed-in twin; a no-op while signed out. */
    fun register(token: String) {
        val twinId = AuthManager.currentTwinIdOrNull() ?: return
        Firebase.firestore.collection("twins").document(twinId)
            // merge + arrayUnion: works before the twin doc exists, never
            // duplicates, and never touches the rest of the profile.
            .set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "Couldn't save the FCM token", it) }
    }
}
