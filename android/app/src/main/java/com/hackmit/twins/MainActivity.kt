package com.hackmit.twins

import com.hackmit.twins.notifications.PushTokenRepository
import androidx.lifecycle.lifecycleScope
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
import com.hackmit.twins.match.MatchTeaserScreen
import com.hackmit.twins.match.RadarScreen
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
    const val MATCH_TEASER = "match_teaser"
    const val RADAR = "radar"
    const val NEGOTIATION_DETAIL = "negotiation_detail"
}

/**
 * State for a pending notification tap (or a Home feed tap on a confirmed
 * match), held outside NavHost args since it doesn't fit cleanly into a
 * nav route string. Two kinds, matching the two notification types:
 *  - Teaser: "your twin found someone" -> MatchTeaserScreen. Only needs a
 *    matchId; everything else is live-fetched from the match doc.
 *  - Radar: "you're both in" -> RadarScreen, once both people approved.
 *    Carries the already-revealed name/photo straight from the push
 *    payload — no extra fetch needed.
 */
private sealed class PendingNav {
    data class Teaser(val matchId: String) : PendingNav()
    data class Radar(val otherTwinId: String, val otherName: String, val otherPhotoUrl: String?) : PendingNav()
}

class MainActivity : ComponentActivity() {

    // Plain Activity field holding the most recent notification tap; read
    // once into Compose state inside setContent (see onCreate below).
    private var latestPendingNav: PendingNav? = null

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

    private val notificationPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // Bluetooth decides whether proximity can run; a declined
            // notification permission must not hold that up.
            if (hasAll(bluetoothPermissions)) {
                startBleService()
            }
            // If denied: we don't nag/re-prompt — BLE proximity just won't
            // work on this device until the user grants it manually.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latestPendingNav = extractPendingNav(intent)

        setContent {
            DigitalTwinsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    var pendingNav by remember { mutableStateOf(latestPendingNav) }

                    LaunchedEffect(pendingNav) {
                        when (pendingNav) {
                            is PendingNav.Teaser -> navController.navigate(Routes.MATCH_TEASER)
                            is PendingNav.Radar -> navController.navigate(Routes.RADAR)
                            null -> {}
                        }
                    }

                    AppNavHost(
                        navController = navController,
                        pendingNav = pendingNav,
                        onNavHandled = { pendingNav = null },
                        onSelectNav = { pendingNav = it },
                        onAuthenticated = { startBlePipeline() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractPendingNav(intent)?.let { latestPendingNav = it }
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
        // A match is delivered as a push, so the twin's profile has to know
        // this device's FCM token (see PushTokenRepository).
        lifecycleScope.launch { PushTokenRepository.syncCurrentToken() }

        if (hasAll(bluetoothPermissions)) startBleService()

        // Ask for everything still missing in one prompt sequence. Android 13+
        // will not show "your twin found someone" (or even the foreground
        // service's own notification) without POST_NOTIFICATIONS.
        val missing = (bluetoothPermissions + notificationPermissions).filterNot { hasAll(arrayOf(it)) }
        if (missing.isNotEmpty()) requestPermissionsLauncher.launch(missing.toTypedArray())
    }

    private fun hasAll(permissions: Array<String>): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Always stops the service first, even if it's not currently running
     * (a harmless no-op in that case). BleProximityService only reads
     * AuthManager's current twinId once, in onCreate() — if it's already
     * alive from an earlier session in this same process (e.g. someone
     * signed out and into a different account, or created a fresh account,
     * without the app process itself restarting), a plain
     * startForegroundService() call only reaches onStartCommand() and the
     * service keeps advertising/negotiating under the STALE twinId
     * indefinitely. Forcing a stop+start here guarantees onCreate() runs
     * again with whoever is actually signed in now.
     */
    private fun startBleService() {
        val intent = Intent(this, BleProximityService::class.java)
        stopService(intent)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun extractPendingNav(intent: Intent?): PendingNav? {
        return when (intent?.action) {
            ACTION_OPEN_TEASER -> {
                val matchId = intent.getStringExtra(EXTRA_MATCH_ID) ?: return null
                PendingNav.Teaser(matchId)
            }
            ACTION_OPEN_RADAR -> {
                val otherTwinId = intent.getStringExtra(EXTRA_MATCHED_TWIN_ID) ?: return null
                PendingNav.Radar(
                    otherTwinId = otherTwinId,
                    otherName = intent.getStringExtra(EXTRA_MATCHED_NAME) ?: "Someone nearby",
                    otherPhotoUrl = intent.getStringExtra(EXTRA_MATCHED_PHOTO_URL),
                )
            }
            else -> null
        }
    }

    companion object {
        const val ACTION_OPEN_TEASER = "com.hackmit.twins.action.OPEN_TEASER"
        const val ACTION_OPEN_RADAR = "com.hackmit.twins.action.OPEN_RADAR"
        const val EXTRA_MATCH_ID = "matchId"
        const val EXTRA_MATCHED_TWIN_ID = "matchedTwinId"
        const val EXTRA_MATCHED_NAME = "matchedName"
        const val EXTRA_MATCHED_PHOTO_URL = "matchedPhotoUrl"
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    pendingNav: PendingNav?,
    onNavHandled: () -> Unit,
    onSelectNav: (PendingNav) -> Unit,
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

    /**
     * Signs out and lands back on Welcome, popping the whole back stack up
     * to [fromRoute] so the signed-out session can't be reached via system
     * back. Also stops BleProximityService — it's still advertising/
     * scanning under the now-signed-out twinId otherwise, since nothing
     * else tears it down on logout.
     */
    fun signOutAndGoToWelcome(fromRoute: String) {
        context.stopService(Intent(context, BleProximityService::class.java))
        AuthManager.signOut()
        navController.navigate(Routes.WELCOME) {
            popUpTo(fromRoute) { inclusive = true }
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
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() },
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
                onBack = { signOutAndGoToWelcome(Routes.ONBOARDING) },
                onSkip = {
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
                    onSelectNav(PendingNav.Teaser(item.matchId))
                    navController.navigate(Routes.MATCH_TEASER)
                },
                onOpenNegotiationDetail = { item ->
                    selectedNegotiationItem = item
                    negotiationDetail = null
                    navController.navigate(Routes.NEGOTIATION_DETAIL)
                },
                onLogout = { signOutAndGoToWelcome(Routes.HOME) },
            )
        }
        composable(Routes.NEGOTIATION_DETAIL) {
            val item = selectedNegotiationItem ?: return@composable
            val twinId = AuthManager.currentTwinIdOrNull() ?: return@composable
            LaunchedEffect(item.matchId) {
                negotiationDetail = MatchFeedRepository.fetchDetail(item.matchId, twinId)
            }
            NegotiationDetailScreen(detail = negotiationDetail)
        }
        composable(Routes.MATCH_TEASER) {
            val teaser = pendingNav as? PendingNav.Teaser ?: return@composable
            val twinId = AuthManager.currentTwinIdOrNull() ?: return@composable
            MatchTeaserScreen(
                matchId = teaser.matchId,
                myTwinId = twinId,
                onCancelled = {
                    onNavHandled()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onRevealed = { otherTwinId, otherName, otherPhotoUrl ->
                    onSelectNav(PendingNav.Radar(otherTwinId, otherName, otherPhotoUrl))
                    navController.navigate(Routes.RADAR) {
                        popUpTo(Routes.MATCH_TEASER) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.RADAR) {
            val radar = pendingNav as? PendingNav.Radar ?: return@composable
            RadarScreen(
                otherTwinId = radar.otherTwinId,
                otherName = radar.otherName,
                otherPhotoUrl = radar.otherPhotoUrl,
            )
        }
    }
}
