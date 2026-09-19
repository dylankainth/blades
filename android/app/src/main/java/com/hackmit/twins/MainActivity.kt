package com.hackmit.twins

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.hackmit.twins.auth.AuthManager
import com.hackmit.twins.auth.SignInScreen
import com.hackmit.twins.auth.SignUpScreen
import com.hackmit.twins.ble.BleProximityService
import com.hackmit.twins.match.MatchScreen
import com.hackmit.twins.onboarding.OnboardingScreen
import com.hackmit.twins.ui.HomePagerScreen
import com.hackmit.twins.ui.WelcomeScreen
import com.hackmit.twins.ui.MatchFeedItem
import com.hackmit.twins.ui.MatchFeedRepository
import com.hackmit.twins.ui.NegotiationDetail
import com.hackmit.twins.ui.NegotiationDetailScreen
import com.hackmit.twins.ui.theme.DigitalTwinsTheme
import kotlinx.coroutines.launch

private object Routes {
    const val WELCOME = "welcome"
    const val SIGN_IN = "sign_in"
    const val SIGN_UP = "sign_up"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val MATCH = "match"
    const val NEGOTIATION_DETAIL = "negotiation_detail"
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
            // If denied: we don't nag/re-prompt — BLE proximity just won't
            // work on this device until the user grants it manually.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latestPendingMatch = extractPendingMatch(intent)

        setContent {
            DigitalTwinsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    var pendingMatch by remember { mutableStateOf(latestPendingMatch) }

                    LaunchedEffect(pendingMatch) {
                        if (pendingMatch != null) {
                            navController.navigate(Routes.MATCH)
                        }
                    }

                    AppNavHost(
                        navController = navController,
                        pendingMatch = pendingMatch,
                        onMatchHandled = { pendingMatch = null },
                        onSelectMatch = { pendingMatch = it },
                        onAuthenticated = { startBlePipeline() },
                    )
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

    /** Called once we actually have a signed-in twinId (fresh sign-in/up,
     *  or an already-persisted Firebase Auth session on cold start). */
    private fun startBlePipeline() {
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

@Composable
private fun AppNavHost(
    navController: NavHostController,
    pendingMatch: PendingMatch?,
    onMatchHandled: () -> Unit,
    onSelectMatch: (PendingMatch) -> Unit,
    onAuthenticated: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedNegotiationItem by remember { mutableStateOf<MatchFeedItem?>(null) }
    var negotiationDetail by remember { mutableStateOf<NegotiationDetail?>(null) }

    val googleSignInClient = remember {
        AuthManager.buildGoogleSignInClient(
            context,
            context.getString(R.string.google_web_client_id),
        )
    }

    /** Routes to Home if this account already finished onboarding, else
     *  Onboarding. Used for sign-in (email/password) and for Google
     *  Sign-In on either screen, since Google sign-in transparently
     *  creates-or-signs-in and this check is correct either way. */
    fun routeCheckingOnboarding(twinId: String) {
        scope.launch {
            val destination = if (AuthManager.hasCompletedOnboarding(twinId)) {
                Routes.HOME
            } else {
                Routes.ONBOARDING
            }
            onAuthenticated()
            navController.navigate(destination) {
                popUpTo(Routes.WELCOME) { inclusive = true }
            }
        }
    }

    /** A fresh email/password sign-up is always a brand-new account with no
     *  twin yet — skip the Firestore check and go straight to Onboarding. */
    fun routeAlwaysToOnboarding() {
        onAuthenticated()
        navController.navigate(Routes.ONBOARDING) {
            popUpTo(Routes.WELCOME) { inclusive = true }
        }
    }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        scope.launch {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    val twinId = AuthManager.signInWithGoogleIdToken(idToken)
                    routeCheckingOnboarding(twinId)
                }
            } catch (e: ApiException) {
                // Cancelled or failed sign-in: just leave the user on the
                // current auth screen to retry, no crash/dead-end.
            }
        }
    }

    // If a Firebase Auth session already exists (returning user, app
    // relaunched), skip Welcome/SignIn entirely and route straight in.
    LaunchedEffect(Unit) {
        AuthManager.currentTwinIdOrNull()?.let { routeCheckingOnboarding(it) }
    }

    NavHost(navController = navController, startDestination = Routes.WELCOME) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onGoToSignIn = { navController.navigate(Routes.SIGN_IN) },
                onGoToSignUp = { navController.navigate(Routes.SIGN_UP) },
            )
        }
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onSignedIn = { email, password ->
                    val twinId = AuthManager.signInWithEmail(email, password)
                    routeCheckingOnboarding(twinId)
                },
                onGoogleClick = { googleLauncher.launch(googleSignInClient.signInIntent) },
                onSwitchToSignUp = {
                    navController.navigate(Routes.SIGN_UP) { popUpTo(Routes.WELCOME) }
                },
            )
        }
        composable(Routes.SIGN_UP) {
            SignUpScreen(
                onSignedUp = { email, password ->
                    AuthManager.signUpWithEmail(email, password)
                    routeAlwaysToOnboarding()
                },
                onGoogleClick = { googleLauncher.launch(googleSignInClient.signInIntent) },
                onSwitchToSignIn = {
                    navController.navigate(Routes.SIGN_IN) { popUpTo(Routes.WELCOME) }
                },
            )
        }
        composable(Routes.ONBOARDING) {
            val twinId = AuthManager.currentTwinIdOrNull() ?: return@composable
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
            val twinId = AuthManager.currentTwinIdOrNull() ?: return@composable
            HomePagerScreen(
                twinId = twinId,
                onOpenMatch = { item ->
                    onSelectMatch(
                        PendingMatch(
                            twinId = item.otherTwinId,
                            name = item.otherName,
                            photoUrl = item.otherPhotoUrl,
                            reason = item.reason ?: "Your twin thinks you two should talk.",
                        ),
                    )
                    navController.navigate(Routes.MATCH)
                },
                onOpenNegotiationDetail = { item ->
                    selectedNegotiationItem = item
                    negotiationDetail = null
                    navController.navigate(Routes.NEGOTIATION_DETAIL)
                },
            )
        }
        composable(Routes.NEGOTIATION_DETAIL) {
            val item = selectedNegotiationItem ?: return@composable
            val twinId = AuthManager.currentTwinIdOrNull() ?: return@composable
            LaunchedEffect(item.matchId) {
                negotiationDetail = MatchFeedRepository.fetchDetail(item.matchId, twinId)
            }
            NegotiationDetailScreen(otherName = item.otherName, detail = negotiationDetail)
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
