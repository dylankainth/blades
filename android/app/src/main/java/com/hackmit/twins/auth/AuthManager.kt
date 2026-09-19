package com.hackmit.twins.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Uses Firebase Anonymous Auth purely to get a stable, unique per-device ID
 * (the Firebase uid) on first launch — this is the "twinId" used everywhere
 * else in the app (BLE advertisement payload, Firestore checkin docs, FCM
 * targeting). No email/password/social credential is required for this;
 * Facebook Login (see OnboardingScreen.kt) is separate and only used to grab
 * a public name + photo for the twin's profile, not for auth.
 */
object AuthManager {

    private val auth: FirebaseAuth get() = Firebase.auth

    /** Returns the current twinId, signing in anonymously first if needed. */
    suspend fun getOrCreateTwinId(): String {
        val existing = auth.currentUser
        if (existing != null) return existing.uid
        val result = auth.signInAnonymously().await()
        return requireNotNull(result.user?.uid) { "Anonymous sign-in returned no user" }
    }

    /** Non-suspending accessor for call sites that know sign-in already happened. */
    fun currentTwinIdOrNull(): String? = auth.currentUser?.uid
}
