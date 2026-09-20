package com.hackmit.twins.auth

import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.delay
import com.hackmit.twins.ui.cute.rememberPressBounce
import com.hackmit.twins.ui.cute.RiseIn
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hackmit.twins.R
import com.hackmit.twins.ui.ListeningAvatar
import com.hackmit.twins.ui.theme.KlickColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val FieldShape = RoundedCornerShape(16.dp)
private val ButtonPadding = PaddingValues(vertical = 18.dp)

/** Long enough for the IME to finish resizing the window before we scroll. */
private const val KEYBOARD_SETTLE_MS = 320L
private const val COMPACT_BELOW_HEIGHT_DP = 700

/**
 * Shared layout for SignInScreen/SignUpScreen: the Klick creature and a
 * headline up top, email/password, a primary submit pill, an "or" rule, a
 * Google pill, and the switch-to-the-other-screen link pinned to the bottom.
 *
 * Top-aligned and scrollable rather than vertically centred, so the fields
 * stay put and visible when the keyboard opens.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AuthFormScreen(
    headline: String,
    subtitle: String,
    submitLabel: String,
    onSubmitEmail: suspend (email: String, password: String) -> Unit,
    onGoogleClick: () -> Unit,
    switchPrompt: String,
    switchActionLabel: String,
    onSwitch: () -> Unit,
    onBack: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Focusing a field opens the keyboard, which used to leave the submit
    // button below the fold. After the keyboard has had a moment to take its
    // space, scroll so the button (and therefore both fields) is on screen.
    val submitInView = remember { BringIntoViewRequester() }
    fun revealSubmit() {
        scope.launch {
            delay(KEYBOARD_SETTLE_MS)
            submitInView.bringIntoView()
        }
    }
    val submitBounce = rememberPressBounce()
    val googleBounce = rememberPressBounce()

    fun submit() {
        if (email.isBlank() || password.isBlank() || isSubmitting) return
        focusManager.clearFocus()
        isSubmitting = true
        errorText = null
        scope.launch {
            try {
                onSubmitEmail(email.trim(), password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorText = e.message ?: "Something went wrong. Try again."
            } finally {
                isSubmitting = false
            }
        }
    }

    Surface(color = KlickColors.PageBackground, modifier = Modifier.fillMaxSize()) {
        // imePadding sits OUTSIDE BoxWithConstraints so maxHeight is the space
        // actually left above the keyboard. The column is at least that tall,
        // which is what lets SpaceBetween pin the switch link to the bottom
        // when there is room and let it follow the form when there is not
        // (weight() cannot do this inside a scrolling column).
        BoxWithConstraints(modifier = Modifier.fillMaxSize().imePadding()) {
        // Short screens (older ~640dp-tall phones): shrink the hero and drop
        // the subtitle so both sign-in routes fit without scrolling. Judged on
        // the full screen height, not the space left above the keyboard, so
        // the layout does not jump when a field is focused.
        val compact = LocalConfiguration.current.screenHeightDp < COMPACT_BELOW_HEIGHT_DP
        val gap = if (compact) 12.dp else 24.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBack,
                // IconButton carries 12dp of its own inset; pull it back so the
                // arrow sits on the same 24dp edge as the text below it.
                modifier = Modifier.padding(top = 8.dp).offset(x = (-12).dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = KlickColors.TextPrimary,
                )
            }

            RiseIn(index = 0) {
                // Start inset: the mascot's rings and dots draw past its bounds.
                ListeningAvatar(
                    size = if (compact) 56.dp else 84.dp,
                    modifier = Modifier.padding(top = gap, start = 14.dp),
                )
            }

            RiseIn(index = 1) {
                Column {
                    Text(
                        text = headline,
                        style = if (compact) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                        color = KlickColors.TextPrimary,
                        modifier = Modifier.padding(top = gap),
                    )
                    if (!compact) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = KlickColors.TextSecondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = KlickColors.TextPrimary,
                unfocusedBorderColor = KlickColors.Border,
                focusedContainerColor = KlickColors.CardSurface,
                unfocusedContainerColor = KlickColors.CardSurface,
                focusedLabelColor = KlickColors.TextPrimary,
                unfocusedLabelColor = KlickColors.TextSecondary,
                cursorColor = KlickColors.TextPrimary,
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                shape = FieldShape,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (compact) 16.dp else 32.dp)
                    .onFocusChanged { if (it.isFocused) revealSubmit() },
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Text(
                            text = if (passwordVisible) "Hide" else "Show",
                            style = MaterialTheme.typography.labelLarge,
                            color = KlickColors.TextSecondary,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                shape = FieldShape,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .onFocusChanged { if (it.isFocused) revealSubmit() },
            )

            errorText?.let { message ->
                Surface(
                    color = KlickColors.InsetSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, KlickColors.Border),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(
                        text = message,
                        color = KlickColors.Accent,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            Button(
                onClick = ::submit,
                enabled = !isSubmitting,
                shape = FieldShape,
                contentPadding = ButtonPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KlickColors.TextPrimary,
                    contentColor = KlickColors.OnDark,
                    disabledContainerColor = KlickColors.TextPrimary,
                    disabledContentColor = KlickColors.OnDark,
                ),
                interactionSource = submitBounce.interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .bringIntoViewRequester(submitInView)
                    .then(submitBounce.modifier),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = KlickColors.OnDark,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(submitLabel, style = MaterialTheme.typography.titleMedium)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 12.dp else 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = KlickColors.Border)
                Text(
                    text = "or",
                    color = KlickColors.TextTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = KlickColors.Border)
            }

            OutlinedButton(
                onClick = onGoogleClick,
                enabled = !isSubmitting,
                shape = FieldShape,
                contentPadding = ButtonPadding,
                border = BorderStroke(1.dp, KlickColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = KlickColors.CardSurface,
                    contentColor = KlickColors.TextPrimary,
                ),
                interactionSource = googleBounce.interactionSource,
                modifier = Modifier.fillMaxWidth().then(googleBounce.modifier),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_google),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Continue with Google", style = MaterialTheme.typography.titleMedium)
            }

          }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = if (compact) 8.dp else 24.dp, bottom = if (compact) 4.dp else 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = switchPrompt,
                    color = KlickColors.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextButton(onClick = onSwitch) {
                    Text(
                        text = switchActionLabel,
                        color = KlickColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        }
    }
}
