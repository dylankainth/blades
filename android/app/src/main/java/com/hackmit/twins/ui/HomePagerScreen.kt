package com.hackmit.twins.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hackmit.twins.context.TwinContextScreen
import kotlinx.coroutines.launch

/**
 * Hosts Home (the idle/listening screen), RecentSearchesScreen (the
 * black/white negotiation feed), and TwinContextScreen ("Everything it
 * knows") as a 3-page vertical pager — a full swipe down from Home reveals
 * Recent, and swiping down again from Recent reveals Context, matching the
 * "pull down for what's above" feel of e.g. a notification shade, rather
 * than a normal forward navigation push. Context is page 0 (bottommost —
 * nothing below it), Recent is page 1 (middle), Home is page 2 (the
 * start) — each swipe down moves to a lower page number.
 *
 * Owns the single live Firestore feed listener so both pages share it
 * rather than each maintaining their own.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePagerScreen(
    twinId: String,
    onOpenMatch: (MatchFeedItem) -> Unit,
    onOpenNegotiationDetail: (MatchFeedItem) -> Unit,
    onLogout: () -> Unit,
) {
    var feed by remember { mutableStateOf<List<MatchFeedItem>>(emptyList()) }
    DisposableEffect(twinId) {
        val registration = MatchFeedRepository.listen(twinId) { feed = it }
        onDispose { registration.remove() }
    }

    val pagerState = rememberPagerState(initialPage = 2) { 3 }
    val scope = rememberCoroutineScope()

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        // Real-device testing showed the un-reversed default mapped swipe
        // up (not down) to Home -> Recent — reversed here so a physical
        // swipe down is what reveals Recent (and Recent -> Context), per
        // the actual ask.
        reverseLayout = true,
    ) { page ->
        when (page) {
            0 -> TwinContextScreen(
                twinId = twinId,
                onBackToRecent = { scope.launch { pagerState.animateScrollToPage(1) } },
            )
            1 -> RecentSearchesScreen(
                feed = feed,
                onOpenMatch = onOpenMatch,
                onOpenNegotiationDetail = onOpenNegotiationDetail,
                onBackToHome = { scope.launch { pagerState.animateScrollToPage(2) } },
                onShowContext = { scope.launch { pagerState.animateScrollToPage(0) } },
            )
            else -> HomeScreen(
                onShowRecent = { scope.launch { pagerState.animateScrollToPage(1) } },
                onLogout = onLogout,
            )
        }
    }
}
