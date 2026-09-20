package com.hackmit.twins.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hackmit.twins.ui.theme.KlickColors
import kotlinx.coroutines.launch

/**
 * Shared layout for SignInScreen/SignUpScreen — email/password fields, a
 * primary submit pill, a divider, and a "Continue with Google" pill.
 * Both screens are otherwise identical, so this is the one place the
 * actual form UI lives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuthFormScreen(
    title: String,
    submitLabel: String,
    onSubmitEmail: suspend (email: String, password: String) -> Unit,
    onGoogleClick: () -> Unit,
    switchPrompt: String,
    switchActionLabel: String,
    onSwitch: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun submit() {
        if (email.isBlank() || password.isBlank() || isSubmitting) return
        isSubmitting = true
        errorText = null
        scope.launch {
            try {
                onSubmitEmail(email.trim(), password)
            } catch (e: Exception) {
                errorText = e.message ?: "Something went wrong. Try again."
            } finally {
                isSubmitting = false
            }
        }
    }

    Scaffold(
        containerColor = KlickColors.PageBackground,
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = KlickColors.TextPrimary,
                unfocusedBorderColor = KlickColors.Border,
                focusedContainerColor = KlickColors.CardSurface,
                unfocusedContainerColor = KlickColors.CardSurface,
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            errorText?.let {
                Text(
                    text = it,
                    color = KlickColors.Accent,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Button(
                onClick = ::submit,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KlickColors.TextPrimary,
                    contentColor = KlickColors.OnDark,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        color = KlickColors.OnDark,
                    )
                } else {
                    Text(submitLabel, style = MaterialTheme.typography.titleMedium)
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "or",
                    color = KlickColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedButton(
                onClick = onGoogleClick,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 18.dp),
                border = BorderStroke(1.dp, KlickColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = KlickColors.TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue with Google", style = MaterialTheme.typography.titleMedium)
            }

            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = switchPrompt,
                    color = KlickColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = switchActionLabel,
                    color = KlickColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(4.dp).clickable(onClick = onSwitch),
                )
            }
        }
    }
}
