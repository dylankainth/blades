package com.hackmit.twins.badge

import com.hackmit.twins.ui.cute.rememberPressBounce
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hackmit.twins.ble.BleProximityService
import com.hackmit.twins.ui.theme.KlickColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "Badge"
private const val PREFS = "klick_badge"
private const val KEY_PAIRED = "paired"

private fun badgePrefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

private fun toast(context: Context, message: String) =
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()

/** Runs a badge action, toasting its result or a friendly failure. Never eats cancellation. */
private fun CoroutineScope.runBadgeAction(
    context: Context,
    failureMessage: String,
    action: suspend () -> String,
) = launch {
    try {
        toast(context, action())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, failureMessage, e)
        toast(context, "$failureMessage ${e.message.orEmpty()}".trim())
    }
}

private suspend fun scanAndPair(context: Context, onPaired: () -> Unit): String {
    val code = BadgeRepository.scanBadgeCode(context) ?: return "That wasn't a Klick badge."
    val owner = BadgeRepository.pair(code)
    badgePrefs(context).edit().putBoolean(KEY_PAIRED, true).apply()
    onPaired()
    return "Badge paired${owner?.let { " — say hi, $it" }.orEmpty()}. Watch it wave."
}

/**
 * The obvious way in: a button on Home that stays until this phone has paired
 * a badge once. After that, re-pairing lives in [BadgeMenu].
 */
@Composable
fun PairBadgeButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var paired by remember { mutableStateOf(badgePrefs(context).getBoolean(KEY_PAIRED, false)) }
    if (paired) return

    val bounce = rememberPressBounce()
    OutlinedButton(
        onClick = {
            scope.runBadgeAction(context, "Couldn't pair that badge.") {
                scanAndPair(context) { paired = true }
            }
        },
        modifier = modifier.then(bounce.modifier),
        interactionSource = bounce.interactionSource,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, KlickColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = KlickColors.TextPrimary),
    ) {
        Text("Got a badge? Scan its QR to pair", style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Overflow menu for the Home top bar: pair (or re-pair) a badge, and reset
 * the demo between judges. Self-contained so Home only has to place it.
 */
@Composable
fun BadgeMenu() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Badge and demo options")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Pair a badge") },
            onClick = {
                expanded = false
                scope.runBadgeAction(context, "Couldn't pair that badge.") {
                    scanAndPair(context) {}
                }
            },
        )
        DropdownMenuItem(
            text = { Text("Reset demo") },
            onClick = {
                expanded = false
                scope.runBadgeAction(context, "Couldn't reset.") {
                    val removed = BadgeRepository.resetDemo()
                    BleProximityService.forgetSeenTokens()
                    "Cleared $removed negotiation${if (removed == 1) "" else "s"}. Ready for the next judge."
                }
            },
        )
    }
}
