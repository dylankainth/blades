package com.hackmit.twins.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.facebook.login.widget.LoginButton
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.hackmit.twins.ui.theme.KlickColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** A single line in the onboarding chat transcript. */
data class ChatMessage(val fromUser: Boolean, val text: String)

/**
 * Conversational twin-creation screen: "tell me about yourself, what are you
 * hoping to get out of this weekend" — see CLAUDE.md. Each user message is
 * sent to the `onboardingChat` HTTPS callable Cloud Function, which is
 * assumed to hold conversation state server-side (keyed by twinId) and
 * return the assistant's next line, plus a flag for when onboarding is
 * considered complete.
 *
 * Facebook Login here is scoped to `public_profile` ONLY (name + photo) —
 * no email, no friends list, no posting permissions. It's used purely to
 * make the twin's card look like a real person to match against, not as an
 * auth mechanism (auth is anonymous Firebase auth, see AuthManager.kt).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    twinId: String,
    onOnboardingComplete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                fromUser = false,
                text = "Hey! I'm building your twin so it can find good people " +
                    "for you to meet. Tell me a bit about yourself and what " +
                    "you're hoping to get out of this weekend.",
            ),
        )
    }
    var input by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var fbName by remember { mutableStateOf<String?>(null) }

    val callbackManager = remember { CallbackManager.Factory.create() }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        // +1: item 0 is the Facebook card, so message i lives at index i + 1.
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || isSending) return
        messages.add(ChatMessage(fromUser = true, text = text))
        input = ""
        isSending = true
        scope.launch {
            try {
                val payload = hashMapOf(
                    "twinId" to twinId,
                    "message" to text,
                )
                val result = Firebase.functions
                    .getHttpsCallable("onboardingChat")
                    .call(payload)
                    .await()

                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any?>
                val reply = data?.get("reply") as? String
                    ?: "Got it — tell me more?"
                val complete = data?.get("onboardingComplete") as? Boolean ?: false

                messages.add(ChatMessage(fromUser = false, text = reply))
                if (complete) onOnboardingComplete()
            } catch (e: Exception) {
                messages.add(
                    ChatMessage(
                        fromUser = false,
                        text = "Hmm, I couldn't reach the server just now. " +
                            "Mind trying again? (${e.message})",
                    ),
                )
            } finally {
                isSending = false
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
        val keyboardOpen = WindowInsets.isImeVisible
        LaunchedEffect(keyboardOpen) {
            if (keyboardOpen && messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) {
                // First item, not pinned above the list: pinned, it ate most of
                // the height left once the keyboard opened and the chat
                // collapsed to nothing. Here it just scrolls away.
                item(key = "facebook-card") {
                    // Facebook Login: public_profile only, for name + photo on the
                    // twin's card. Wrapped in AndroidView since the official SDK's
                    // LoginButton is a plain Android View, not a Compose component.
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
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
                                            callbackManager,
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
                }
                items(messages) { message -> ChatBubble(message) }
            }

            if (isSending) {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KlickColors.TextPrimary,
                        unfocusedBorderColor = KlickColors.Border,
                        focusedContainerColor = KlickColors.CardSurface,
                        unfocusedContainerColor = KlickColors.CardSurface,
                    ),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = { sendMessage(input) },
                    enabled = !isSending,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KlickColors.TextPrimary,
                        contentColor = KlickColors.OnDark,
                    ),
                ) {
                    Text("Send", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.fromUser) KlickColors.TextPrimary else KlickColors.CardSurface,
            ),
            border = if (message.fromUser) null else BorderStroke(1.dp, KlickColors.Border),
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.fromUser) KlickColors.OnDark else KlickColors.TextPrimary,
            )
        }
    }
}
