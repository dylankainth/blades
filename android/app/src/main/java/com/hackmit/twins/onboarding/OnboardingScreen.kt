package com.hackmit.twins.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.hackmit.twins.ui.ListeningAvatar
import com.hackmit.twins.ui.cute.RiseIn
import com.hackmit.twins.ui.theme.KlickColors
import com.hackmit.twins.ui.theme.SpaceGroteskFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.hackmit.twins.voice.VoiceRepository

private const val TOTAL_STEPS = 3

/**
 * Twin-creation flow — three steps, mirroring the product's step-counter/
 * dot-pagination pattern used elsewhere for this kind of guided setup:
 *
 * 1. "Connect your context" — three connect points, each independent and
 *    optional: a free-text/voice dump (submitContext.ts), an Instagram
 *    handle (public web search via importSocialContext.ts), and a LinkedIn
 *    PDF upload. Each one writes to the backend the moment it succeeds
 *    (tapping its circle), not batched behind a single submit — "Continue"
 *    just moves to the next step.
 * 2. "Set your boundaries" — what the twin is/isn't allowed to bring up
 *    during negotiation (submitBoundaries.ts; enforced in
 *    negotiateTwins.ts's persona prompt, not as a filter on the extracted
 *    profile). This call also flips onboardingComplete, since it's reached
 *    regardless of which/whether any step-1 source was connected.
 * 3. "You're in" — completion screen; "Enter Klick" hands off to Home.
 */
@Composable
fun OnboardingScreen(
    twinId: String,
    onOnboardingComplete: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var step by remember { mutableStateOf(0) }
    // Deliberate theater once step 3's "Enter Klick" is tapped: the real
    // work (submitContext/importSocialContext/submitBoundaries) already
    // finished during steps 1-2, so this is pure ceremony before Home —
    // see TwinBuildingScreen's own header comment.
    var showBuilding by remember { mutableStateOf(false) }

    // Step 1 back to sign-in (root of this flow); step 2/3 back to the
    // previous step. Mirrors the top-left arrow's own onClick below.
    BackHandler { if (step == 0) onBack() else step -= 1 }

    // ---- Step 1: connect sources ------------------------------------

    var contextConnected by remember { mutableStateOf(false) }
    var instagramConnected by remember { mutableStateOf(false) }
    var linkedinConnected by remember { mutableStateOf(false) }

    var showContextDialog by remember { mutableStateOf(false) }
    var showInstagramDialog by remember { mutableStateOf(false) }

    var textDump by remember { mutableStateOf("") }
    var isSubmittingContext by remember { mutableStateOf(false) }
    var contextError by remember { mutableStateOf<String?>(null) }
    var isListening by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    // You talked to it, so it talks back once your context is saved.
    var usedVoice by remember { mutableStateOf(false) }

    fun startListening() {
        try {
            VoiceRepository.startRecording(context)
            isListening = true
        } catch (e: Exception) {
            Log.e("Onboarding", "Couldn't start the microphone", e)
            Toast.makeText(context, "Couldn't start the microphone.", Toast.LENGTH_SHORT).show()
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startListening()
        else Toast.makeText(context, "No mic access, so typing it is.", Toast.LENGTH_SHORT).show()
    }

    fun submitContextDump() {
        val dump = textDump.trim()
        if (dump.isEmpty() || isSubmittingContext) return
        isSubmittingContext = true
        contextError = null
        scope.launch {
            try {
                Firebase.functions
                    .getHttpsCallable("submitContext")
                    .call(hashMapOf("twinId" to twinId, "textDump" to dump))
                    .await()
                contextConnected = true
                showContextDialog = false
                if (usedVoice) {
                    VoiceRepository.speakInBackground(
                        context,
                        "Got it. I'll keep an eye out for people worth meeting, and only interrupt you when it counts.",
                    )
                }
            } catch (e: Exception) {
                contextError = "Couldn't save that just now — mind trying again? (${e.message})"
            } finally {
                isSubmittingContext = false
            }
        }
    }

    var instagramHandle by remember { mutableStateOf("") }
    var isSubmittingInstagram by remember { mutableStateOf(false) }
    var instagramStatus by remember { mutableStateOf<String?>(null) }

    fun submitInstagram() {
        val handle = instagramHandle.trim().removePrefix("@")
        if (handle.isEmpty() || isSubmittingInstagram) return
        isSubmittingInstagram = true
        instagramStatus = null
        scope.launch {
            try {
                val result = Firebase.functions
                    .getHttpsCallable("importSocialContext")
                    .call(
                        hashMapOf(
                            "twinId" to twinId,
                            "provider" to "instagram",
                            "instagramHandle" to handle,
                        ),
                    )
                    .await()
                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any?>
                instagramStatus = data?.get("message") as? String ?: "Done."
                instagramConnected = true
            } catch (e: Exception) {
                instagramStatus = "Couldn't reach the server just now. (${e.message})"
            } finally {
                isSubmittingInstagram = false
            }
        }
    }

    var isImportingLinkedin by remember { mutableStateOf(false) }
    var linkedinStatus by remember { mutableStateOf<String?>(null) }

    // LinkedIn: no API worth building against for a weekend (it's invite-
    // only partner access) — instead the client reads whatever PDF the user
    // picks (meant to be LinkedIn's own "Save to PDF" profile export),
    // base64-encodes it, and folds it into the same importSocialContext
    // pipeline as Instagram (see importSocialContext.ts's "linkedin"
    // provider + lib/linkedinPdf.ts for server-side extraction).
    // No dialog needed — tapping the circle goes straight to the file picker.
    val linkedinPdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isImportingLinkedin = true
            linkedinStatus = null
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Couldn't open that file.")
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val result = Firebase.functions
                    .getHttpsCallable("importSocialContext")
                    .call(
                        hashMapOf(
                            "twinId" to twinId,
                            "provider" to "linkedin",
                            "pdfBase64" to base64,
                        ),
                    )
                    .await()
                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any?>
                linkedinStatus = data?.get("message") as? String ?: "Done."
                linkedinConnected = true
            } catch (e: Exception) {
                linkedinStatus = "Couldn't read that PDF. (${e.message})"
            } finally {
                isImportingLinkedin = false
            }
        }
    }

    // ---- Step 2: boundaries ------------------------------------------

    var careerOn by remember { mutableStateOf(true) }
    var personalOn by remember { mutableStateOf(true) }
    var deeplyPersonalOn by remember { mutableStateOf(false) }
    var isSavingBoundaries by remember { mutableStateOf(false) }

    fun saveBoundariesAndAdvance() {
        if (isSavingBoundaries) return
        isSavingBoundaries = true
        scope.launch {
            try {
                Firebase.functions
                    .getHttpsCallable("submitBoundaries")
                    .call(
                        hashMapOf(
                            "twinId" to twinId,
                            "career" to careerOn,
                            "personalInterests" to personalOn,
                            "deeplyPersonalHistory" to deeplyPersonalOn,
                        ),
                    )
                    .await()
            } catch (e: Exception) {
                // Best-effort: defaults apply server-side (see
                // DEFAULT_BOUNDARIES) if this write never lands, and the
                // user isn't blocked from finishing onboarding over it.
                Log.e("Onboarding", "submitBoundaries failed", e)
            } finally {
                isSavingBoundaries = false
                // Show the "building your twin" beat between steps 2 and 3
                // (not after step 3) — it's the transition into "You're in",
                // not a hand-off to Home. TwinBuildingScreen's onComplete
                // below advances to step 2 once it finishes.
                showBuilding = true
            }
        }
    }

    Scaffold(containerColor = KlickColors.PageBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (step == 0) onBack() else step -= 1 }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = KlickColors.TextPrimary,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // Only on step 1 — never trap someone here, but the escape
                // hatch doesn't need to clutter steps 2/3.
                if (step == 0) {
                    TextButton(onClick = onSkip) {
                        Text("Skip for now", color = KlickColors.TextSecondary)
                    }
                }
            }

            // Step 3 ("You're in") is a standalone completion beat, not
            // another form to fill in — no step counter, and everything
            // centered in the middle of the page rather than anchored top,
            // unlike steps 1-2's guided-form layout.
            val isCompleteStep = step == 2
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = if (isCompleteStep) Arrangement.Center else Arrangement.Top,
                horizontalAlignment = if (isCompleteStep) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                if (!isCompleteStep) {
                    Text(
                        text = "STEP ${step + 1} OF $TOTAL_STEPS",
                        style = MaterialTheme.typography.labelMedium,
                        color = KlickColors.TextSecondary,
                        letterSpacing = 0.08.em,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = when (step) {
                        0 -> "Connect your\ncontext"
                        1 -> "Set your\nboundaries"
                        else -> "You're in"
                    },
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    lineHeight = 44.sp,
                    letterSpacing = (-0.02).em,
                    color = KlickColors.TextPrimary,
                    textAlign = if (isCompleteStep) TextAlign.Center else TextAlign.Start,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = when (step) {
                        0 -> "Link the accounts that already say who you are. Nothing is posted, nothing is public."
                        1 -> "Choose what your twin can talk about, and what stays off-limits — always visible, always editable."
                        else -> "Your twin is ready. It'll work quietly in the background and only interrupt you when it matters."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = KlickColors.TextSecondary,
                    textAlign = if (isCompleteStep) TextAlign.Center else TextAlign.Start,
                )
                Spacer(modifier = Modifier.height(32.dp))

                when (step) {
                    0 -> ConnectStep(
                        contextConnected = contextConnected,
                        instagramConnected = instagramConnected,
                        linkedinConnected = linkedinConnected,
                        isImportingLinkedin = isImportingLinkedin,
                        onTapContext = { showContextDialog = true },
                        onTapInstagram = { showInstagramDialog = true },
                        onTapLinkedin = { linkedinPdfPicker.launch("application/pdf") },
                    )
                    1 -> BoundariesStep(
                        careerOn = careerOn,
                        onCareerChange = { careerOn = it },
                        personalOn = personalOn,
                        onPersonalChange = { personalOn = it },
                        deeplyPersonalOn = deeplyPersonalOn,
                        onDeeplyPersonalChange = { deeplyPersonalOn = it },
                    )
                    else -> CompleteStep()
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepDots(current = step, total = TOTAL_STEPS)
                Button(
                    onClick = {
                        when (step) {
                            0 -> step = 1
                            1 -> saveBoundariesAndAdvance()
                            else -> onOnboardingComplete()
                        }
                    },
                    enabled = !isSavingBoundaries,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KlickColors.TextPrimary,
                        contentColor = KlickColors.OnDark,
                    ),
                ) {
                    if (isSavingBoundaries) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            color = KlickColors.OnDark,
                        )
                    } else {
                        Text(
                            text = if (step == TOTAL_STEPS - 1) "Enter Klick" else "Continue",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }

    if (showContextDialog) {
        ConnectDialog(
            onDismiss = { showContextDialog = false },
            title = "Tell us about yourself",
            // Pinned below the scrolling body, so Save stays on screen
            // however much text is in the box.
            actions = {
                if (contextError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(contextError ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { submitContextDump() },
                    enabled = textDump.isNotBlank() && !isSubmittingContext,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KlickColors.TextPrimary, contentColor = KlickColors.OnDark),
                ) {
                    if (isSubmittingContext) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), color = KlickColors.OnDark)
                    } else {
                        Text("Save", style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
        ) {
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
                // Without a cap the field grows with its text: a 10k+ character
                // paste made it thousands of dp tall. Past 8 lines it scrolls
                // internally instead.
                maxLines = 8,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KlickColors.TextPrimary,
                    unfocusedBorderColor = KlickColors.Border,
                ),
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    when {
                        isListening -> {
                            isListening = false
                            isTranscribing = true
                            scope.launch {
                                try {
                                    val heard = VoiceRepository.stopAndTranscribe()
                                    if (heard.isBlank()) {
                                        Toast.makeText(context, "Didn't catch that.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        textDump = listOf(textDump.trim(), heard).filter { it.isNotEmpty() }.joinToString(" ")
                                        usedVoice = true
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.e("Onboarding", "Transcription failed", e)
                                    Toast.makeText(context, "Couldn't hear that. Try typing it.", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isTranscribing = false
                                }
                            }
                        }
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED -> startListening()
                        else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !isSubmittingContext && !isTranscribing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (isListening) KlickColors.TextPrimary else KlickColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KlickColors.TextPrimary),
            ) {
                Text(
                    text = when {
                        isListening -> "Listening… tap to stop"
                        isTranscribing -> "Writing that down…"
                        else -> "Or just tell me out loud"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }

    if (showInstagramDialog) {
        ConnectDialog(onDismiss = { showInstagramDialog = false }, title = "Instagram") {
            Text(
                text = "No login needed — just your handle. We search what's publicly visible.",
                style = MaterialTheme.typography.bodyMedium,
                color = KlickColors.TextSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
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
                ),
            )
            if (instagramStatus != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(instagramStatus ?: "", style = MaterialTheme.typography.bodySmall, color = KlickColors.TextSecondary)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = { submitInstagram() },
                enabled = instagramHandle.isNotBlank() && !isSubmittingInstagram,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KlickColors.TextPrimary, contentColor = KlickColors.OnDark),
            ) {
                if (isSubmittingInstagram) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), color = KlickColors.OnDark)
                } else {
                    Text("Connect", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showBuilding) {
        TwinBuildingScreen(
            onComplete = {
                showBuilding = false
                step = 2
            },
        )
    }
}

@Composable
private fun ConnectStep(
    contextConnected: Boolean,
    instagramConnected: Boolean,
    linkedinConnected: Boolean,
    isImportingLinkedin: Boolean,
    onTapContext: () -> Unit,
    onTapInstagram: () -> Unit,
    onTapLinkedin: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        RiseIn(index = 0) {
            ConnectCircle(label = "✎", caption = "Context", connected = contextConnected, onClick = onTapContext)
        }
        RiseIn(index = 1) {
            ConnectCircle(label = "IG", caption = "Instagram", connected = instagramConnected, onClick = onTapInstagram)
        }
        RiseIn(index = 2) {
            ConnectCircle(
                label = "in",
                caption = "LinkedIn",
                connected = linkedinConnected,
                loading = isImportingLinkedin,
                onClick = onTapLinkedin,
            )
        }
    }
}

@Composable
private fun ConnectCircle(
    label: String,
    caption: String,
    connected: Boolean,
    onClick: () -> Unit,
    loading: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (connected) KlickColors.TextPrimary else KlickColors.InsetSurface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = if (connected) KlickColors.OnDark else KlickColors.TextSecondary,
                )
            } else {
                Text(
                    text = label,
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (connected) KlickColors.OnDark else KlickColors.TextSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (connected) "Connected" else "Connect",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (connected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (connected) KlickColors.TextPrimary else KlickColors.TextSecondary,
        )
    }
}

@Composable
private fun BoundariesStep(
    careerOn: Boolean,
    onCareerChange: (Boolean) -> Unit,
    personalOn: Boolean,
    onPersonalChange: (Boolean) -> Unit,
    deeplyPersonalOn: Boolean,
    onDeeplyPersonalChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RiseIn(index = 0) {
            BoundaryRow("Career & projects", careerOn) { onCareerChange(!careerOn) }
        }
        RiseIn(index = 1) {
            BoundaryRow("Personal interests", personalOn) { onPersonalChange(!personalOn) }
        }
        RiseIn(index = 2) {
            BoundaryRow("Deeply personal history", deeplyPersonalOn) { onDeeplyPersonalChange(!deeplyPersonalOn) }
        }
    }
}

@Composable
private fun BoundaryRow(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KlickColors.InsetSurface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = KlickColors.TextPrimary)
        Text(
            text = if (on) "On" else "Off",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = if (on) KlickColors.TextPrimary else KlickColors.TextTertiary,
        )
    }
}

@Composable
private fun CompleteStep() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        RiseIn { ListeningAvatar(size = 108.dp) }
    }
}

@Composable
private fun StepDots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (i == current) 22.dp else 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (i == current) KlickColors.TextPrimary else KlickColors.Border),
            )
        }
    }
}

@Composable
private fun ConnectDialog(
    onDismiss: () -> Unit,
    title: String,
    actions: (@Composable ColumnScopeContent)? = null,
    content: @Composable ColumnScopeContent,
) {
    Dialog(onDismissRequest = onDismiss) {
        // A Dialog is its own window: the activity's adjustResize and the
        // Scaffold's imePadding don't reach it, and dialog themes default to
        // adjustPan, which can leave the bottom of the card under the
        // keyboard. Resize instead, so the card is laid out in the space
        // above the keyboard.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        @Suppress("DEPRECATION")
        val adjustResize = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        SideEffect { dialogWindow?.setSoftInputMode(adjustResize) }
        Surface(
            // Zero once the window has resized; only matters if the keyboard
            // still ends up overlapping the dialog's window.
            modifier = Modifier.imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = KlickColors.CardSurface,
            border = BorderStroke(1.dp, KlickColors.Border),
        ) {
            // The card is capped at 560dp (less while the keyboard is up) and
            // only the body scrolls: the title and `actions` stay pinned, so
            // Save is on screen no matter how much text is in the box. An
            // earlier fix capped and scrolled the whole card, but the text
            // field inside still grew without bound, which left Save
            // thousands of dp of scrolling below a long paste.
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 560.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = KlickColors.TextPrimary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    content()
                }
                actions?.invoke(this)
            }
        }
    }
}

private typealias ColumnScopeContent = androidx.compose.foundation.layout.ColumnScope.() -> Unit
