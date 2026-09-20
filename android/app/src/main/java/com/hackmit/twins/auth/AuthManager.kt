package com.hackmit.twins.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Real accounts (email/password, or Google) rather than the anonymous-only
 * model this started as — see MainActivity's WelcomeScreen -> SignIn/SignUp
 * flow. The Firebase Auth uid is still what everything else in the app
 * calls "twinId" (BLE advertisement payload, Firestore checkin docs, FCM
 * targeting) — that part didn't change, just how a uid gets established.
 *
 * Facebook Login (see OnboardingScreen.kt) is separate and unrelated to
 * auth — it only grabs a public name + photo for the twin's profile card.
 */
object AuthManager {

    private val auth: FirebaseAuth get() = Firebase.auth
    private val db get() = Firebase.firestore

    /** Non-suspending accessor for call sites that know sign-in already happened. */
    fun currentTwinIdOrNull(): String? = auth.currentUser?.uid

    /** Drops the Firebase session so Welcome can offer a different account. */
    fun signOut() = auth.signOut()

    suspend fun signInWithEmail(email: String, password: String): String {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return requireNotNull(result.user?.uid) { "Sign-in returned no user" }
    }

    suspend fun signUpWithEmail(email: String, password: String): String {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return requireNotNull(result.user?.uid) { "Sign-up returned no user" }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): String {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        return requireNotNull(result.user?.uid) { "Google sign-in returned no user" }
    }

    /**
     * Reads twins/{twinId}.onboardingComplete — the flag onboardingChat.ts
     * writes once the onboarding interview actually finishes (see
     * functions/src/onboardingChat.ts). Used to decide, right after
     * sign-in/sign-up, whether to route to Home or to Onboarding.
     */
    suspend fun hasCompletedOnboarding(twinId: String): Boolean {
        val snap = db.collection("twins").document(twinId).get().await()
        return snap.getBoolean("onboardingComplete") ?: false
    }

    /** Builds the GoogleSignInClient used to launch the account picker. */
    fun buildGoogleSignInClient(context: Context, webClientId: String): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, options)
    }
}
