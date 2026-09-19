package com.hackmit.twins.auth

import androidx.compose.runtime.Composable

/**
 * Per the app-open flow: "I've already got one" on the welcome screen
 * leads here. A brand-new account never has a completed twin profile, so
 * MainActivity always routes a successful sign-up straight to Onboarding
 * — see MainActivity's post-auth routing.
 */
@Composable
fun SignUpScreen(
    onSignedUp: suspend (email: String, password: String) -> Unit,
    onGoogleClick: () -> Unit,
    onSwitchToSignIn: () -> Unit,
) {
    AuthFormScreen(
        title = "Create your account",
        submitLabel = "Sign up",
        onSubmitEmail = onSignedUp,
        onGoogleClick = onGoogleClick,
        switchPrompt = "Already have an account?",
        switchActionLabel = "Sign in",
        onSwitch = onSwitchToSignIn,
    )
}
