package com.hackmit.twins.auth

import androidx.compose.runtime.Composable

/**
 * Per the app-open flow: "Build your twin" on the welcome screen leads
 * here. After a successful sign-in, MainActivity checks whether this
 * account already has a completed twin profile and routes to Home (if so)
 * or Onboarding (if not) — see AuthManager.hasCompletedOnboarding and
 * MainActivity's post-auth routing.
 */
@Composable
fun SignInScreen(
    onSignedIn: suspend (email: String, password: String) -> Unit,
    onGoogleClick: () -> Unit,
    onSwitchToSignUp: () -> Unit,
    onBack: () -> Unit,
) {
    AuthFormScreen(
        headline = "Welcome back",
        subtitle = "Your twin has been keeping an eye out for you.",
        submitLabel = "Sign in",
        onSubmitEmail = onSignedIn,
        onGoogleClick = onGoogleClick,
        switchPrompt = "New here?",
        switchActionLabel = "Create an account",
        onSwitch = onSwitchToSignUp,
        onBack = onBack,
    )
}
