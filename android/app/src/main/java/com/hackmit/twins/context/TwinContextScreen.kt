package com.hackmit.twins.context

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginResult
import com.facebook.login.widget.LoginButton
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.hackmit.twins.ui.ListeningAvatar
import com.hackmit.twins.ui.cute.MascotBubble
import com.hackmit.twins.ui.theme.KlickColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * "Everything it knows" — the transparency screen: every categorized fact
 * the twin's context extraction has pulled out (twins/{twinId}.facts, see
 * functions/src/lib/extractProfile.ts), one per swipeable/tappable card,
 * each individually editable or removable, plus the same connect-more-
 * sources circles from onboarding so a user can enrich their twin without
 * re-running the whole onboarding flow.
 *
 * Lives beneath RecentSearchesScreen as the third page of HomePagerScreen's
 * vertical pager — Home -> swipe down -> Recent searches -> swipe down
 * again -> here. Nothing below this page.
 */
@Composable
fun TwinContextScreen(twinId: String, onBackToRecent: () -> Unit) {
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf(TwinContextSnapshot(emptyList(), emptyList(), TwinConnections())) }
    DisposableEffect(twinId) {
        val registration = TwinContextRepository.listen(twinId) { snapshot = it }
        onDispose { registration.remove() }
    }

    var cardIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(snapshot.facts.size) {
        if (cardIndex >= snapshot.facts.size) cardIndex = 0
    }

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showContextDialog by remember { mutableStateOf(false) }
    var showInstagramDialog by remember { mutableStateOf(false) }
    var showFacebookDialog by remember { mutableStateOf(false) }

    Scaffold(containerColor = KlickColors.PageBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Nothing lives below this screen — only a way back up.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBackToRecent)
                    .padding(top = 12.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Swipe up for recent searches",
                    tint = KlickColors.TextSecondary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Everything it knows",
                        style = MaterialTheme.typography.headlineLarge,
                        color = KlickColors.TextPrimary,
                    )
                    Text(
                        text = "Full transparency. Edit or remove anytime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KlickColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                ListeningAvatar(size = 44.dp, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "WHAT IT'S LEARNED",
                    style = MaterialTheme.typography.labelLarge,
                    color = KlickColors.TextSecondary,
                    letterSpacing = 0.08.em,
                )
                if (snapshot.facts.isNotEmpty()) {
                    Text(
                        text = "${cardIndex + 1} / ${snapshot.facts.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = KlickColors.TextSecondary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (snapshot.facts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    MascotBubble(
                        text = "Nothing learned yet — the more you tell it, the sharper the " +
                            "matches. Add some context from onboarding any time.",
                    )
                }
            } else {
                FactCardStack(
                    fact = snapshot.facts[cardIndex],
                    hasMultiple = snapshot.facts.size > 1,
                    onTapAdvance = { cardIndex = (cardIndex + 1) % snapshot.facts.size },
                    onEdit = { editingIndex = cardIndex },
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Tap the card to see the next one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KlickColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = KlickColors.Border)
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "CONNECT MORE OF YOU",
                style = MaterialTheme.typography.labelLarge,
                color = KlickColors.TextSecondary,
                letterSpacing = 0.08.em,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ConnectStatusCircle(
                    label = "✎",
                    caption = "Context",
                    connected = snapshot.connections.context,
                    onClick = { showContextDialog = true },
                )
                ConnectStatusCircle(
                    label = "IG",
                    caption = "Instagram",
                    connected = snapshot.connections.instagram,
                    onClick = { showInstagramDialog = true },
                )
                LinkedinConnectCircle(twinId = twinId, connected = snapshot.connections.linkedin)
                ConnectStatusCircle(
                    label = "FB",
                    caption = "Facebook",
                    connected = snapshot.connections.facebook,
                    onClick = { showFacebookDialog = true },
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    val editIndex = editingIndex
    if (editIndex != null && editIndex < snapshot.facts.size) {
        EditFactDialog(
            fact = snapshot.facts[editIndex],
            onDismiss = { editingIndex = null },
            onSave = { newDetail ->
                scope.launch {
                    val updated = snapshot.facts.toMutableList()
                    updated[editIndex] = updated[editIndex].copy(detail = newDetail, edited = true)
                    TwinContextRepository.saveFacts(twinId, updated)
                    editingIndex = null
                }
            },
            onDelete = {
                scope.launch {
                    val updated = snapshot.facts.toMutableList().also { it.removeAt(editIndex) }
                    TwinContextRepository.saveFacts(twinId, updated)
                    editingIndex = null
                }
            },
        )
    }

    if (showContextDialog) {
        ContextConnectDialog(twinId = twinId, onDismiss = { showContextDialog = false })
    }
    if (showInstagramDialog) {
        InstagramConnectDialog(twinId = twinId, onDismiss = { showInstagramDialog = false })
    }
    if (showFacebookDialog) {
        FacebookConnectDialog(onDismiss = { showFacebookDialog = false })
    }
}

@Composable
private fun FactCardStack(
    fact: TwinFact,
    hasMultiple: Boolean,
    onTapAdvance: () -> Unit,
    onEdit: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        // A second card peeking out from behind, hinting there's more to
        // cycle through — only worth showing when there actually is.
        if (hasMultiple) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.92f)
                    .offset(y = 10.dp)
                    .height(150.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = KlickColors.InsetSurface),
                border = BorderStroke(1.dp, KlickColors.Border),
            ) {}
        }
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onTapAdvance),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = KlickColors.CardSurface),
            border = BorderStroke(1.dp, KlickColors.Border),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = fact.category,
                    style = MaterialTheme.typography.labelLarge,
                    color = KlickColors.TextSecondary,
                    letterSpacing = 0.04.em,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = fact.detail,
                    style = MaterialTheme.typography.titleMedium,
                    color = KlickColors.TextPrimary,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEdit) {
                        Text("✎ Edit", color = KlickColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditFactDialog(
    fact: TwinFact,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(fact) { mutableStateOf(fact.detail) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = KlickColors.CardSurface,
            border = BorderStroke(1.dp, KlickColors.Border),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(fact.category, style = MaterialTheme.typography.titleLarge, color = KlickColors.TextPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KlickColors.TextPrimary,
                        unfocusedBorderColor = KlickColors.Border,
                    ),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDelete) {
                        Text("Remove", color = KlickColors.TextSecondary)
                    }
                    Button(
                        onClick = { onSave(text.trim()) },
                        enabled = text.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KlickColors.TextPrimary,
                            contentColor = KlickColors.OnDark,
                        ),
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectStatusCircle(
    label: String,
    caption: String,
    connected: Boolean,
    onClick: () -> Unit,
    loading: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (connected) KlickColors.TextPrimary else KlickColors.InsetSurface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = if (connected) KlickColors.OnDark else KlickColors.TextSecondary,
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (connected) KlickColors.OnDark else KlickColors.TextSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (connected) "Connected" else "Connect",
            style = MaterialTheme.typography.bodySmall,
            color = if (connected) KlickColors.TextPrimary else KlickColors.TextSecondary,
        )
    }
}

@Composable
private fun ConnectDialogShell(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = KlickColors.CardSurface,
            border = BorderStroke(1.dp, KlickColors.Border),
        ) {
            // A Dialog's content otherwise grows unbounded — a long context
            // text dump pushed the Save/Connect button off the bottom of
            // the screen with no way to reach it. Capping the height and
            // scrolling within it keeps the button reachable regardless of
            // how much text is in the box.
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = KlickColors.TextPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}

/**
 * Lets a twin's text-dump context be added (or refreshed) without
 * repeating onboarding — same submitContext.ts call as OnboardingScreen's
 * ConnectStep, just reachable any time from the "Connect more of you" row.
 */
@Composable
private fun ContextConnectDialog(twinId: String, onDismiss: () -> Unit) {
    var textDump by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ConnectDialogShell(title = "Tell us about yourself", onDismiss = onDismiss) {
        Text(
            text = "Paste anything — a bio, notes on what you're working on, what you're hoping to get out of this weekend.",
            style = MaterialTheme.typography.bodyMedium,
            color = KlickColors.TextSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = textDump,
            onValueChange = { textDump = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. I'm a CS student building a founder community...") },
            minLines = 5,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = KlickColors.TextPrimary,
                unfocusedBorderColor = KlickColors.Border,
            ),
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = {
                val dump = textDump.trim()
                if (dump.isEmpty() || isSubmitting) return@Button
                isSubmitting = true
                error = null
                scope.launch {
                    try {
                        Firebase.functions
                            .getHttpsCallable("submitContext")
                            .call(hashMapOf("twinId" to twinId, "textDump" to dump))
                            .await()
                        onDismiss()
                    } catch (e: Exception) {
                        error = "Couldn't save that just now — mind trying again? (${e.message})"
                    } finally {
                        isSubmitting = false
                    }
                }
            },
            enabled = textDump.isNotBlank() && !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KlickColors.TextPrimary, contentColor = KlickColors.OnDark),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = KlickColors.OnDark)
            } else {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun InstagramConnectDialog(twinId: String, onDismiss: () -> Unit) {
    var handle by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ConnectDialogShell(title = "Instagram", onDismiss = onDismiss) {
        Text(
            text = "No login needed — just your handle. We search what's publicly visible.",
            style = MaterialTheme.typography.bodyMedium,
            color = KlickColors.TextSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = handle,
            onValueChange = { handle = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("@yourhandle") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = KlickColors.TextPrimary,
                unfocusedBorderColor = KlickColors.Border,
            ),
        )
        if (status != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(status ?: "", style = MaterialTheme.typography.bodySmall, color = KlickColors.TextSecondary)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = {
                val cleaned = handle.trim().removePrefix("@")
                if (cleaned.isEmpty() || isSubmitting) return@Button
                isSubmitting = true
                status = null
                scope.launch {
                    try {
                        val result = Firebase.functions
                            .getHttpsCallable("importSocialContext")
                            .call(
                                hashMapOf(
                                    "twinId" to twinId,
                                    "provider" to "instagram",
                                    "instagramHandle" to cleaned,
                                ),
                            )
                            .await()
                        @Suppress("UNCHECKED_CAST")
                        val data = result.data as? Map<String, Any?>
                        status = data?.get("message") as? String ?: "Done."
                    } catch (e: Exception) {
                        status = "Couldn't reach the server just now. (${e.message})"
                    } finally {
                        isSubmitting = false
                    }
                }
            },
            enabled = handle.isNotBlank() && !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KlickColors.TextPrimary, contentColor = KlickColors.OnDark),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = KlickColors.OnDark)
            } else {
                Text("Connect", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * LinkedIn skips a dialog entirely — same as onboarding's ConnectStep —
 * since tapping the circle goes straight to the file picker for a
 * "Save to PDF" LinkedIn profile export (see lib/linkedinPdf.ts).
 */
@Composable
private fun LinkedinConnectCircle(twinId: String, connected: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isImporting = true
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Couldn't open that file.")
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                Firebase.functions
                    .getHttpsCallable("importSocialContext")
                    .call(
                        hashMapOf(
                            "twinId" to twinId,
                            "provider" to "linkedin",
                            "pdfBase64" to base64,
                        ),
                    )
                    .await()
            } catch (_: Exception) {
                // Best-effort, same as onboarding — the rest of the twin's
                // context still stands on its own.
            } finally {
                isImporting = false
            }
        }
    }

    ConnectStatusCircle(
        label = "in",
        caption = "LinkedIn",
        connected = connected,
        loading = isImporting,
        onClick = { if (!isImporting) picker.launch("application/pdf") },
    )
}

@Composable
private fun FacebookConnectDialog(onDismiss: () -> Unit) {
    val callbackManager = remember { CallbackManager.Factory.create() }
    var connectedName by remember { mutableStateOf<String?>(null) }

    ConnectDialogShell(title = "Facebook", onDismiss = onDismiss) {
        Text(
            text = connectedName?.let { "Connected." }
                ?: "public_profile only — just your name and photo, nothing else.",
            style = MaterialTheme.typography.bodyMedium,
            color = KlickColors.TextSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        AndroidView(
            factory = { ctx ->
                LoginButton(ctx).apply {
                    setPermissions("public_profile")
                    registerCallback(
                        callbackManager,
                        object : FacebookCallback<LoginResult> {
                            override fun onSuccess(result: LoginResult) {
                                connectedName = result.accessToken.userId
                                // TODO: same gap as OnboardingScreen.kt — fetch
                                // /me?fields=name,picture via a GraphRequest and
                                // persist onto the twin profile doc.
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
