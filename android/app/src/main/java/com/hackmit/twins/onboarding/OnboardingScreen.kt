package com.hackmit.twins.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginResult
import com.facebook.login.widget.LoginButton
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.hackmit.twins.ui.theme.KlickColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Twin-creation screen — see CLAUDE.md's two-tier context model.
 *
 * Tier A (everyone): a single "paste anything about yourself" text dump
 * plus an optional Instagram handle field, both submitted together by the
 * one "Build my twin" action. The text dump goes to the `submitContext`
 * callable; the handle (if given) goes to `importSocialContext`'s
 * `instagram` provider, which runs a public web search for it via
 * Parallel (see lib/parallel.ts) rather than any OAuth flow — it works for
 * any handle, not just tester accounts. The Instagram search is
 * best-effort: it's awaited before navigating away (since this screen's
 * coroutine scope is cancelled on navigation) but never blocks onboarding
 * on failure — the text dump alone is always enough context on its own.
 * There's also the existing Facebook Login button scoped to
 * `public_profile` only (name + photo).
 *
 * Tier B (tester/role accounts on the Meta App only — see
 * importSocialContext.ts): an optional "Connect Facebook posts" button
 * that pulls real post text via Graph API. For any account without a role
 * on the Meta App, this fails gracefully with a friendly message rather
 * than an error — the text dump above is always enough on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    twinId: String,
    onOnboardingComplete: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var textDump by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    var fbName by remember { mutableStateOf<String?>(null) }
    var instagramHandle by remember { mutableStateOf("") }
    var socialStatusMessage by remember { mutableStateOf<String?>(null) }
    var isImportingSocial by remember { mutableStateOf(false) }

    // Two separate CallbackManager instances — one per LoginButton — rather
    // than sharing one across both widgets. The Facebook SDK dispatches
    // login results by a fixed request code shared across ALL login
    // attempts on a given CallbackManager, so registering two independent
    // callbacks (name/photo vs. user_posts) on the *same* manager risks
    // both firing for either button's result. Isolating them per-widget
    // avoids that ambiguity entirely.
    val nameLoginCallbackManager = remember { CallbackManager.Factory.create() }
    val postsLoginCallbackManager = remember { CallbackManager.Factory.create() }

    fun submitTextDump() {
        val dump = textDump.trim()
        if (dump.isEmpty() || isSubmitting) return
        isSubmitting = true
        submitError = null
        scope.launch {
            try {
                val payload = hashMapOf(
                    "twinId" to twinId,
                    "textDump" to dump,
                )
                Firebase.functions
                    .getHttpsCallable("submitContext")
                    .call(payload)
                    .await()

                // Optional Instagram handle, submitted alongside the text
                // dump as part of the same action. Awaited here (not fired
                // off separately) so it actually runs to completion before
                // we navigate away and this screen's coroutine scope gets
                // cancelled — but any failure here is swallowed, never
                // blocking onboarding. See the file header comment.
                val handle = instagramHandle.trim().removePrefix("@")
                if (handle.isNotEmpty()) {
                    try {
                        Firebase.functions
                            .getHttpsCallable("importSocialContext")
                            .call(
                                hashMapOf(
                                    "twinId" to twinId,
                                    "provider" to "instagram",
                                    "instagramHandle" to handle,
                                ),
                            )
                            .await()
                    } catch (_: Exception) {
                        // Best-effort — no worries, the text dump above is
                        // always enough context on its own.
                    }
                }

                onOnboardingComplete()
            } catch (e: Exception) {
                submitError = "Couldn't save that just now — mind trying again? (${e.message})"
            } finally {
                isSubmitting = false
            }
        }
    }

    fun importSocialContext(provider: String, extra: Map<String, String>) {
        isImportingSocial = true
        socialStatusMessage = null
        scope.launch {
            try {
                val payload = hashMapOf<String, Any>(
                    "twinId" to twinId,
                    "provider" to provider,
                )
                payload.putAll(extra)
                val result = Firebase.functions
                    .getHttpsCallable("importSocialContext")
                    .call(payload)
                    .await()
                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any?>
                socialStatusMessage = data?.get("message") as? String
                    ?: "Done — check your twin's context."
            } catch (e: Exception) {
                socialStatusMessage = "Couldn't reach the server just now. (${e.message})"
            } finally {
                isImportingSocial = false
            }
        }
    }

    Scaffold(
        containerColor = KlickColors.PageBackground,
        topBar = {
            TopAppBar(
                title = { Text("Build your twin") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KlickColors.PageBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Tell us about yourself",
                style = MaterialTheme.typography.titleMedium,
                color = KlickColors.TextPrimary,
            )
            Text(
                text = "Paste anything — a bio, notes on what you're working on, what " +
                    "you're hoping to get out of this weekend. Your twin uses this to " +
                    "find people worth meeting.",
                style = MaterialTheme.typography.bodyMedium,
                color = KlickColors.TextSecondary,
            )

            OutlinedTextField(
                value = textDump,
                onValueChange = { textDump = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. I'm a CS student building a founder community...") },
                minLines = 6,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KlickColors.TextPrimary,
                    unfocusedBorderColor = KlickColors.Border,
                    focusedContainerColor = KlickColors.CardSurface,
                    unfocusedContainerColor = KlickColors.CardSurface,
                ),
            )

            // Optional — no OAuth, works for any handle: submitted together
            // with the text dump above, this runs a public web search for
            // it via Parallel (see importSocialContext.ts / lib/parallel.ts)
            // rather than Graph API.
            Text(
                text = "Instagram handle (optional)",
                style = MaterialTheme.typography.bodyMedium,
                color = KlickColors.TextPrimary,
            )
            OutlinedTextField(
                value = instagramHandle,
                onValueChange = { instagramHandle = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("@yourhandle") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KlickColors.TextPrimary,
                    unfocusedBorderColor = KlickColors.Border,
                    focusedContainerColor = KlickColors.CardSurface,
                    unfocusedContainerColor = KlickColors.CardSurface,
                ),
            )

            if (submitError != null) {
                Text(
                    text = submitError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = { submitTextDump() },
                enabled = textDump.isNotBlank() && !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KlickColors.TextPrimary,
                    contentColor = KlickColors.OnDark,
                ),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Text("Build my twin", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Facebook Login: public_profile only, for name + photo on the
            // twin's card. Wrapped in AndroidView since the official SDK's
            // LoginButton is a plain Android View, not a Compose component.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = KlickColors.CardSurface),
                border = BorderStroke(1.dp, KlickColors.Border),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = fbName?.let { "Signed in as $it" }
                            ?: "Optional: add your name + photo via Facebook",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KlickColors.TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AndroidView(
                        factory = { ctx ->
                            LoginButton(ctx).apply {
                                // public_profile ONLY — no email, no friends,
                                // no posting permissions. See CLAUDE.md.
                                setPermissions("public_profile")
                                registerCallback(
                                    nameLoginCallbackManager,
                                    object : FacebookCallback<LoginResult> {
                                        override fun onSuccess(result: LoginResult) {
                                            fbName = result.accessToken.userId
                                            // TODO: fetch /me?fields=name,picture via a
                                            // GraphRequest and store on the twin profile
                                            // doc alongside twinId.
                                        }

                                        override fun onCancel() {}

                                        override fun onError(error: FacebookException) {}
                                    },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Tier B (see CLAUDE.md): a real Graph API pull, but only
            // functional for accounts added as a Tester/Developer/Admin on
            // the Meta App while it's in Development Mode. For every other
            // account this fails gracefully — the text dump above already
            // covers everyone.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = KlickColors.CardSurface),
                border = BorderStroke(1.dp, KlickColors.Border),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Beta: connect Facebook posts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KlickColors.TextPrimary,
                    )
                    Text(
                        text = "Only works on a small set of connected demo accounts for " +
                            "now — everyone else, no worries, the text dump above is what " +
                            "your twin uses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KlickColors.TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Classic Facebook Login, but requesting `user_posts` in
                    // addition to public_profile — a separate LoginButton
                    // instance from the name/photo one above, since it needs
                    // a different (broader) permission set.
                    AndroidView(
                        factory = { ctx ->
                            LoginButton(ctx).apply {
                                text = "Connect Facebook posts"
                                setPermissions("public_profile", "user_posts")
                                registerCallback(
                                    postsLoginCallbackManager,
                                    object : FacebookCallback<LoginResult> {
                                        override fun onSuccess(result: LoginResult) {
                                            importSocialContext(
                                                "facebook",
                                                mapOf("accessToken" to result.accessToken.token),
                                            )
                                        }

                                        override fun onCancel() {}

                                        override fun onError(error: FacebookException) {
                                            socialStatusMessage =
                                                "Facebook connect failed: ${error.message}"
                                        }
                                    },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (isImportingSocial) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    }
                    if (socialStatusMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = socialStatusMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = KlickColors.TextSecondary,
                        )
                    }
                }
            }

            // Room to breathe below the last card when scrolled to the bottom.
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
