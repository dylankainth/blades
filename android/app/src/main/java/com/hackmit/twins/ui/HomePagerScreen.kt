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
import kotlinx.coroutines.launch

/**
 * Hosts Home (the idle/listening screen) and RecentSearchesScreen (the
 * black/white negotiation feed) as a 2-page vertical pager — a full swipe
 * down from Home reveals Recent, matching the "pull down for what's above"
 * feel of e.g. a notification shade, rather than a normal forward
 * navigation push. Recent is page 0 (above), Home is page 1 (the start).
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

    val pagerState = rememberPagerState(initialPage = 1) { 2 }
    val scope = rememberCoroutineScope()

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        // Real-device testing showed the un-reversed default mapped swipe
        // up (not down) to Home -> Recent — reversed here so a physical
        // swipe down is what reveals Recent, per the actual ask.
        reverseLayout = true,
    ) { page ->
        when (page) {
            0 -> RecentSearchesScreen(
                feed = feed,
                onOpenMatch = onOpenMatch,
                onOpenNegotiationDetail = onOpenNegotiationDetail,
                onBackToHome = { scope.launch { pagerState.animateScrollToPage(1) } },
            )
            else -> HomeScreen(
                onShowRecent = { scope.launch { pagerState.animateScrollToPage(0) } },
                onLogout = onLogout,
            )
        }
    }
}
