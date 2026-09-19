package com.hackmit.twins

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hackmit.twins.auth.AuthManager
import com.hackmit.twins.ble.BleProximityService
import com.hackmit.twins.checkin.CheckinScreen
import com.hackmit.twins.match.MatchScreen
import com.hackmit.twins.onboarding.OnboardingScreen
import com.hackmit.twins.ui.HomeScreen
import com.hackmit.twins.ui.WelcomeScreen
import com.hackmit.twins.ui.theme.DigitalTwinsTheme

private object Routes {
    const val WELCOME = "welcome"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CHECKIN = "checkin"
    const val MATCH = "match"
}

/** State for a pending match notification tap, held outside NavHost args
 *  since the photo URL / reason text don't fit cleanly into a nav route. */
private data class PendingMatch(
    val twinId: String,
    val name: String,
    val photoUrl: String?,
    val reason: String,
)

class MainActivity : ComponentActivity() {

    // Plain Activity field holding the most recent match-notification tap;
    // read once into Compose state inside setContent (see onCreate below).
    private var latestPendingMatch: PendingMatch? = null

    private val bluetoothPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            emptyArray()
        }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) {
                startBleService()
            }
            // If denied: for a hackathon demo we just fall back to the
            // manual CheckinScreen trigger; we don't nag/re-prompt.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latestPendingMatch = extractPendingMatch(intent)

        setContent {
            DigitalTwinsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    var twinId by remember { mutableStateOf<String?>(null) }
                    var pendingMatch by remember { mutableStateOf(latestPendingMatch) }

                    LaunchedEffect(Unit) {
                        val id = AuthManager.getOrCreateTwinId()
                        twinId = id
                        requestBlePermissionsAndStart()
                    }

                    LaunchedEffect(pendingMatch) {
                        if (pendingMatch != null) {
                            navController.navigate(Routes.MATCH)
                        }
                    }

                    val id = twinId
                    if (id != null) {
                        AppNavHost(
                            navController = navController,
                            twinId = id,
                            pendingMatch = pendingMatch,
                            onMatchHandled = { pendingMatch = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractPendingMatch(intent)?.let { latestPendingMatch = it }
        // Note: with singleTop launch mode + Compose state living in
        // setContent's recomposition scope, a fresh notification tap while
        // the Activity is already open re-enters here; wiring this into the
        // already-composed state (rather than just the field above) would
        // need a shared state holder/ViewModel — left as a TODO since for
        // the hackathon demo the app is typically relaunched fresh from the
        // notification tap.
    }

    private fun requestBlePermissionsAndStart() {
        val allGranted = bluetoothPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            startBleService()
        } else if (bluetoothPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(bluetoothPermissions)
        }
    }

    private fun startBleService() {
        ContextCompat.startForegroundService(this, Intent(this, BleProximityService::class.java))
    }

    private fun extractPendingMatch(intent: Intent?): PendingMatch? {
        if (intent?.action != ACTION_OPEN_MATCH) return null
        val twinId = intent.getStringExtra(EXTRA_MATCHED_TWIN_ID) ?: return null
        return PendingMatch(
            twinId = twinId,
            name = intent.getStringExtra(EXTRA_MATCHED_NAME) ?: "Someone nearby",
            photoUrl = intent.getStringExtra(EXTRA_MATCHED_PHOTO_URL),
            reason = intent.getStringExtra(EXTRA_MATCH_REASON)
                ?: "Your twin thinks you two should talk.",
        )
    }

    companion object {
        const val ACTION_OPEN_MATCH = "com.hackmit.twins.action.OPEN_MATCH"
        const val EXTRA_MATCHED_TWIN_ID = "matchedTwinId"
        const val EXTRA_MATCHED_NAME = "matchedName"
        const val EXTRA_MATCHED_PHOTO_URL = "matchedPhotoUrl"
        const val EXTRA_MATCH_REASON = "matchReason"
    }
}

@androidx.compose.runtime.Composable
private fun AppNavHost(
    navController: NavHostController,
    twinId: String,
    pendingMatch: PendingMatch?,
    onMatchHandled: () -> Unit,
) {
    NavHost(navController = navController, startDestination = Routes.WELCOME) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onBuildTwin = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
                onSkipToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                twinId = twinId,
                onOnboardingComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenCheckin = { navController.navigate(Routes.CHECKIN) },
            )
        }
        composable(Routes.CHECKIN) {
            CheckinScreen(
                twinId = twinId,
                onCheckedIn = { navController.popBackStack() },
            )
        }
        composable(Routes.MATCH) {
            val match = pendingMatch
            if (match != null) {
                MatchScreen(
                    matchedName = match.name,
                    matchedPhotoUrl = match.photoUrl,
                    reason = match.reason,
                    onSayHiConfirmed = {
                        onMatchHandled()
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    },
                )
            }
        }
    }
}
