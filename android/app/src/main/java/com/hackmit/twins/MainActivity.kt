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
import androidx.core.os.bundleOf
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

    fun toBundle(): Bundle = when (this) {
        is Teaser -> bundleOf(KEY_KIND to KIND_TEASER, KEY_MATCH_ID to matchId)
        is Radar -> bundleOf(
            KEY_KIND to KIND_RADAR,
            KEY_OTHER_TWIN_ID to otherTwinId,
            KEY_OTHER_NAME to otherName,
            KEY_OTHER_PHOTO_URL to otherPhotoUrl,
        )
    }

    companion object {
        private const val KEY_KIND = "kind"
        private const val KEY_MATCH_ID = "matchId"
        private const val KEY_OTHER_TWIN_ID = "otherTwinId"
        private const val KEY_OTHER_NAME = "otherName"
        private const val KEY_OTHER_PHOTO_URL = "otherPhotoUrl"
        private const val KIND_TEASER = "teaser"
        private const val KIND_RADAR = "radar"

        fun fromBundle(bundle: Bundle?): PendingNav? = when (bundle?.getString(KEY_KIND)) {
            KIND_TEASER -> bundle.getString(KEY_MATCH_ID)?.let { Teaser(it) }
            KIND_RADAR -> bundle.getString(KEY_OTHER_TWIN_ID)?.let { otherTwinId ->
                Radar(
                    otherTwinId = otherTwinId,
                    otherName = bundle.getString(KEY_OTHER_NAME) ?: "Someone nearby",
                    otherPhotoUrl = bundle.getString(KEY_OTHER_PHOTO_URL),
                )
            }
            else -> null
        }
    }
}

class MainActivity : ComponentActivity() {

    // Most recent notification tap. Snapshot state on the Activity (not
    // remembered inside setContent) so onNewIntent can drive navigation while
    // the UI is already composed.
    private var pendingNav by mutableStateOf<PendingNav?>(null)

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
        // A recreated Activity (process death, config change) still holds the
        // old intent, which would reopen a teaser the user has already dealt
        // with. Take what was actually open from the saved state instead: the
        // NavController restores the Teaser/Radar route by itself, and that
        // route has nothing to show without its pendingNav.
        pendingNav = if (savedInstanceState == null) {
            extractPendingNav(intent)
        } else {
            PendingNav.fromBundle(savedInstanceState.getBundle(STATE_PENDING_NAV))
        }

        setContent {
            DigitalTwinsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    LaunchedEffect(pendingNav) {
                        when (pendingNav) {
                            // singleTop: screens that set pendingNav also navigate
                            // themselves, and must not end up stacked twice.
                            is PendingNav.Teaser ->
                                navController.navigate(Routes.MATCH_TEASER) { launchSingleTop = true }
                            is PendingNav.Radar ->
                                navController.navigate(Routes.RADAR) { launchSingleTop = true }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(STATE_PENDING_NAV, pendingNav?.toBundle())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractPendingNav(intent)?.let { pendingNav = it }
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
            else -> extractFromPushData(intent)
        }
    }

    /**
     * While the app is backgrounded FCM displays the push itself, and tapping
     * it launches us with the plain launcher action plus the message's data
     * payload as string extras (keys as sent by notifyMatch.ts and
     * submitMatchApproval.ts) instead of the intent TwinMessagingService builds.
     */
    private fun extractFromPushData(intent: Intent?): PendingNav? {
        val extras = intent?.extras ?: return null
        return when (extras.getString(PUSH_KEY_ACTION)) {
            PUSH_ACTION_OPEN_TEASER ->
                extras.getString(EXTRA_MATCH_ID)?.takeIf { it.isNotBlank() }?.let { PendingNav.Teaser(it) }
            PUSH_ACTION_OPEN_RADAR -> {
                val otherTwinId = extras.getString(PUSH_KEY_OTHER_TWIN_ID)?.takeIf { it.isNotBlank() }
                    ?: return null
                PendingNav.Radar(
                    otherTwinId = otherTwinId,
                    otherName = extras.getString(PUSH_KEY_OTHER_NAME)?.takeIf { it.isNotBlank() }
                        ?: "Someone nearby",
                    otherPhotoUrl = extras.getString(PUSH_KEY_OTHER_PHOTO_URL)?.takeIf { it.isNotBlank() },
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

        private const val STATE_PENDING_NAV = "pendingNav"
        private const val PUSH_KEY_ACTION = "action"
        private const val PUSH_ACTION_OPEN_TEASER = "open_teaser"
        private const val PUSH_ACTION_OPEN_RADAR = "open_radar"
        private const val PUSH_KEY_OTHER_TWIN_ID = "otherTwinId"
        private const val PUSH_KEY_OTHER_NAME = "otherName"
        private const val PUSH_KEY_OTHER_PHOTO_URL = "otherPhotoUrl"
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
            val teaser = pendingNav as? PendingNav.Teaser
            if (teaser == null) {
                // Restored after the Activity was recreated: the route came back
                // but its arguments did not. Go Home rather than show a blank screen.
                LaunchedEffect(Unit) { navController.popBackStack(Routes.HOME, inclusive = false) }
                return@composable
            }
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
            val radar = pendingNav as? PendingNav.Radar
            if (radar == null) {
                LaunchedEffect(Unit) { navController.popBackStack(Routes.HOME, inclusive = false) }
                return@composable
            }
            val twinId = AuthManager.currentTwinIdOrNull() ?: return@composable
            RadarScreen(
                myTwinId = twinId,
                otherTwinId = radar.otherTwinId,
                otherName = radar.otherName,
                otherPhotoUrl = radar.otherPhotoUrl,
                onMet = {
                    onNavHandled()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
    }
}
