package com.hackmit.twins.badge

import android.util.Log
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hackmit.twins.ble.BleProximityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val TAG = "BadgeMenu"

/**
 * Overflow menu for the Home top bar: pair a badge by scanning its QR, and
 * reset the demo between judges. Self-contained so Home only has to place it.
 */
@Composable
fun BadgeMenu() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    fun runAction(failureMessage: String, action: suspend () -> String) {
        expanded = false
        scope.launch {
            try {
                toast(action())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, failureMessage, e)
                toast("$failureMessage ${e.message.orEmpty()}".trim())
            }
        }
    }

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Badge and demo options")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Pair a badge") },
            onClick = {
                runAction("Couldn't pair that badge.") {
                    val code = BadgeRepository.scanBadgeCode(context)
                        ?: return@runAction "That wasn't a Kindred badge."
                    val owner = BadgeRepository.pair(code)
                    "Badge paired${owner?.let { " — say hi, $it" }.orEmpty()}. Watch it wave."
                }
            },
        )
        DropdownMenuItem(
            text = { Text("Reset demo") },
            onClick = {
                runAction("Couldn't reset.") {
                    val removed = BadgeRepository.resetDemo()
                    BleProximityService.forgetSeenTokens()
                    "Cleared $removed negotiation${if (removed == 1) "" else "s"}. Ready for the next judge."
                }
            },
        )
    }
}
