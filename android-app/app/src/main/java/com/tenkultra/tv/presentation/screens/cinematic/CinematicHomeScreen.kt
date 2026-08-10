package com.tenkultra.tv.presentation.screens.cinematic

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tenkultra.tv.domain.model.Channel
import com.tenkultra.tv.domain.model.VodItem
import com.tenkultra.tv.presentation.common.BrandWordmark
import com.tenkultra.tv.presentation.common.LoadingDots
import com.tenkultra.tv.presentation.common.LocalParentalGate
import com.tenkultra.tv.presentation.common.SearchBox
import com.tenkultra.tv.presentation.common.onTap
import com.tenkultra.tv.presentation.common.premiumFocus
import com.tenkultra.tv.presentation.theme.appPalette

private enum class CzZone { TOPBAR, RAILS }

/** Beyond this many items away, animating just makes the user wait — jump straight there. */
private const val SNAP_DISTANCE = 3

/**
 * Scrolls to [index]: a smooth glide for a single step (which is what a D-pad press looks like),
 * an INSTANT jump for anything further. Holding the D-pad, or landing on the far end of a long
 * rail, therefore lands immediately instead of playing a long catch-up animation for every
 * key repeat.
 */
private suspend fun LazyListState.snapOrAnimateTo(index: Int) {
    val distance = kotlin.math.abs(index - firstVisibleItemIndex)
    if (distance > SNAP_DISTANCE) scrollToItem(index) else animateScrollToItem(index)
}

private val EDGE = 48.dp          // consistent left/right page margin (Netflix-style)
private val TOP_BAR_HEIGHT = 72.dp // reserved for the chrome so hero art never sits under it
private const val POSTER_RATIO = 0.68f  // portal poster aspect (w:h) — used by the hero card and the rails
private const val HERO_WEIGHT = 0.56f
private const val RAILS_WEIGHT = 0.44f

/**
 * "Cinematic Hero" layout — Netflix-style. Hero band on top (follows the focused title/channel) and
 * a rails band below with a rail for EVERY Live TV genre (channels) and EVERY VOD category
 * (movies/series) the portal sends — each with its heading (Punjabi, Hindi, News…). Rails load
 * LAZILY as they scroll into view. A search field in the top bar searches VOD (movies + series).
 */
@Composable
fun CinematicHomeScreen(
    onOpenSettings: () -> Unit,
    onOpenVodItem: (VodItem, String) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    /** Resume straight into the player from the "Continue watching" rail (cmd, title, episode). */
    onResumeVod: (String, String, Int) -> Unit,
    viewModel: CinematicHomeViewModel = hiltViewModel()
) {
    val palette = appPalette
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    // CHILD LOCK — the gate works at CATEGORY level: an adult genre/category rail needs the PIN,
    // everything inside a normal rail opens exactly as before.
    val gate = LocalParentalGate.current

    var zone by remember { mutableStateOf(CzZone.RAILS) }
    var topIndex by remember { mutableIntStateOf(0) } // 0 = search, 1 = settings
    var railIndex by remember { mutableIntStateOf(0) }
    var itemIndex by remember { mutableIntStateOf(0) }
    var searchFocused by remember { mutableStateOf(false) }
    // Top-level toggle so the screen isn't crowded: show EITHER Live TV rails OR VOD rails.
    var liveMode by remember { mutableStateOf(true) }

    val searchMode = state.searchQuery.isNotBlank()
    val rows: List<CinematicRail> = when {
        searchMode -> listOf(CinematicRail("__search", "Search results", isLive = false, vod = state.searchResults, loaded = true))
        // Personal rails ("Continue watching", "My list") are VOD-side, so they ride along in VOD mode.
        else -> state.rails.filter { it.isLive == liveMode }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(rows.size) {
        if (railIndex > rows.lastIndex) railIndex = rows.lastIndex.coerceAtLeast(0)
    }
    // Lazy load: warm the focused rail + its neighbours as the user scrolls down (map the filtered
    // row index back to its position in the full rails list).
    LaunchedEffect(railIndex, rows.size, searchMode, liveMode) {
        if (!searchMode) for (i in railIndex..railIndex + 2) {
            val rail = rows.getOrNull(i) ?: continue
            viewModel.ensureRail(state.rails.indexOfFirst { it.id == rail.id && it.isLive == rail.isLive })
        }
    }

    val focusedRail = rows.getOrNull(railIndex)
    val focusedVod: VodItem? = focusedRail?.takeIf { !it.isLive }?.vod?.getOrNull(itemIndex)
    val focusedChannel: Channel? = focusedRail?.takeIf { it.isLive }?.channels?.getOrNull(itemIndex)

    val railListState = rememberLazyListState()
    LaunchedEffect(railIndex) {
        if (railIndex in rows.indices) railListState.snapOrAnimateTo(railIndex)
    }
    LaunchedEffect(state.loading, rows.size) {
        if (!state.loading && rows.isEmpty()) zone = CzZone.TOPBAR
    }

    fun clampItem() {
        val last = (rows.getOrNull(railIndex)?.size ?: 1) - 1
        itemIndex = itemIndex.coerceIn(0, last.coerceAtLeast(0))
    }

    fun openFocused() {
        val rail = rows.getOrNull(railIndex) ?: return
        val i = itemIndex
        if (rail.isLive) {
            rail.channels.getOrNull(i)?.let { ch ->
                // CHILD LOCK — the rail's genre title is what's gated, not the channel name.
                gate.guard(rail.title) {
                    viewModel.prepareLivePlayback(rail, i); onPlayChannel(ch)
                }
            }
            return
        }
        val item = rail.vod.getOrNull(i) ?: return
        // CHILD LOCK — same for movies / series: gated by their VOD category rail.
        gate.guard(rail.title) {
            if (rail.kind == RailKind.CONTINUE) {
                // Resume goes STRAIGHT into the player at the exact episode that was left off,
                // skipping the detail screen — that's the whole point of the rail.
                val series = rail.seriesByCmd[item.cmd] ?: 0
                viewModel.prepareVodPlayback(item, series)
                onResumeVod(item.cmd, item.name, series)
            } else {
                viewModel.prepareVodPlayback(item)
                onOpenVodItem(item, rail.id)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (searchFocused) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (zone) {
                    CzZone.TOPBAR -> when (event.key) {
                        // Top bar slots: 0 = Live TV, 1 = VOD, 2 = Search, 3 = Settings.
                        Key.DirectionLeft -> { if (topIndex > 0) topIndex--; true }
                        Key.DirectionRight -> { if (topIndex < 3) topIndex++; true }
                        Key.DirectionDown -> { if (rows.isNotEmpty()) zone = CzZone.RAILS; true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            when (topIndex) {
                                0 -> { liveMode = true; railIndex = 0; itemIndex = 0 }
                                1 -> { liveMode = false; railIndex = 0; itemIndex = 0 }
                                2 -> runCatching { searchFocus.requestFocus() }
                                else -> onOpenSettings()
                            }
                            true
                        }
                        else -> false
                    }
                    CzZone.RAILS -> when (event.key) {
                        Key.DirectionUp -> {
                            if (railIndex > 0) { railIndex--; clampItem() } else zone = CzZone.TOPBAR
                            true
                        }
                        Key.DirectionDown -> {
                            if (railIndex < rows.lastIndex) { railIndex++; clampItem() }
                            true
                        }
                        Key.DirectionLeft -> { if (itemIndex > 0) itemIndex-- else zone = CzZone.TOPBAR; true }
                        Key.DirectionRight -> {
                            val last = (rows.getOrNull(railIndex)?.size ?: 1) - 1
                            if (itemIndex < last) itemIndex++
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { openFocused(); true }
                        else -> false
                    }
                }
            }
    ) {
        when {
            state.loading && rows.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingDots() }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    HeroBand(
                        vod = focusedVod,
                        channel = focusedChannel,
                        // A half-watched title says "Resume", not "Play".
                        resume = focusedRail?.kind == RailKind.CONTINUE,
                        // CHILD LOCK — no backdrop preview while an adult rail is highlighted.
                        locked = gate.isRestricted(focusedRail?.title),
                        topBarActive = zone == CzZone.TOPBAR,
                        topIndex = topIndex,
                        liveMode = liveMode,
                        searchQuery = state.searchQuery,
                        onSearchChange = viewModel::onSearchQuery,
                        searchFocusRequester = searchFocus,
                        onSearchFocusChanged = { f ->
                            searchFocused = f
                            if (f) zone = CzZone.TOPBAR
                            else runCatching { focus.requestFocus() }
                        },
                        onSettings = onOpenSettings,
                        modifier = Modifier.fillMaxWidth().weight(HERO_WEIGHT)
                    )
                    if (rows.isEmpty() || (searchMode && state.searchResults.isEmpty())) {
                        Box(Modifier.fillMaxWidth().weight(RAILS_WEIGHT), contentAlignment = Alignment.Center) {
                            Text(
                                when {
                                    searchMode && state.searching -> "Searching…"
                                    searchMode -> "No results for \"${state.searchQuery}\""
                                    else -> state.error ?: "No content available."
                                },
                                color = palette.textSecondary, fontSize = 16.sp
                            )
                        }
                    } else {
                        RailsList(
                            rows = rows,
                            railListState = railListState,
                            activeRail = railIndex,
                            activeItem = itemIndex,
                            railsActive = zone == CzZone.RAILS,
                            onFocusRailItem = { r, i -> railIndex = r; itemIndex = i; zone = CzZone.RAILS },
                            onOpenRailItem = { r, i -> railIndex = r; itemIndex = i; openFocused() },
                            modifier = Modifier.fillMaxWidth().weight(RAILS_WEIGHT)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBand(
    vod: VodItem?,
    channel: Channel?,
    resume: Boolean,
    locked: Boolean,
    topBarActive: Boolean,
    topIndex: Int,
    liveMode: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    searchFocusRequester: FocusRequester,
    onSearchFocusChanged: (Boolean) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = appPalette
    // CHILD LOCK — while a locked category rail is highlighted the hero shows the plain
    // placeholder instead of auto-previewing its artwork.
    val backdrop = if (locked) null else (vod?.posterUrl ?: channel?.logoUrl)
    Box(modifier) {
        Crossfade(targetState = backdrop, animationSpec = tween(450), label = "backdrop") { url ->
            if (url != null) {
                Box(Modifier.fillMaxSize()) {
                    // Netflix-style hero with the only artwork a Stalker portal gives us: a small
                    // PORTRAIT poster.
                    //  1) A blurred, zoomed copy washes the FULL band, so the hero reads as one
                    //     wide backdrop instead of a panel bolted onto the right.
                    //  2) The poster itself is shown as a sharp KEY-ART CARD at its true aspect,
                    //     sized DOWN from its native pixels — so it is crisp, never stretched.
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        alpha = 0.55f,
                        modifier = Modifier.fillMaxSize().scale(1.18f).blur(60.dp)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = EDGE + 24.dp)
                            .fillMaxHeight(0.80f)
                            .aspectRatio(POSTER_RATIO)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(palette.surface), contentAlignment = Alignment.Center) {
                    if (locked) {
                        Icon(Icons.Filled.Lock, null, tint = palette.textSecondary, modifier = Modifier.size(44.dp))
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to palette.background,
                    0.34f to palette.background.copy(alpha = 0.90f),
                    0.58f to palette.background.copy(alpha = 0.45f),
                    0.86f to Color.Transparent
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to palette.background.copy(alpha = 0.55f),
                    0.35f to Color.Transparent,
                    // Long fade into the rails band: a short one left a visible seam where the
                    // dimmed backdrop stopped against the page.
                    0.58f to Color.Transparent,
                    1f to palette.background
                )
            )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(TOP_BAR_HEIGHT + 24.dp)
                .background(
                    Brush.verticalGradient(
                        0f to palette.background.copy(alpha = 0.92f),
                        1f to Color.Transparent
                    )
                )
        )
        Column(Modifier.fillMaxSize()) {
            TopBar(
                active = topBarActive,
                topIndex = topIndex,
                liveMode = liveMode,
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                searchFocusRequester = searchFocusRequester,
                onSearchFocusChanged = onSearchFocusChanged,
                onSettings = onSettings
            )
            Spacer(Modifier.weight(1f))
            // Key on the focused title so the block cross-fades as the cursor moves along a rail.
            Crossfade(
                targetState = Triple(vod, channel, resume),
                animationSpec = tween(220),
                label = "heroDetails"
            ) { (v, ch, res) ->
                when {
                    ch != null -> HeroChannelDetails(ch)
                    v != null -> HeroDetails(v, res)
                    else -> Spacer(Modifier.height(1.dp))
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    active: Boolean,
    topIndex: Int,
    liveMode: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    searchFocusRequester: FocusRequester,
    onSearchFocusChanged: (Boolean) -> Unit,
    onSettings: () -> Unit
) {
    val palette = appPalette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = EDGE, end = EDGE, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The brand mark sits top-left, Netflix-style — the wordmark, not a boxed app icon.
        BrandWordmark(modifier = Modifier.width(104.dp), glow = false)
        Spacer(Modifier.width(22.dp))
        // Live TV / VOD toggle — declutters (one mode's rails at a time).
        ModeChip("Live TV", on = liveMode, focused = active && topIndex == 0)
        Spacer(Modifier.width(8.dp))
        ModeChip("VOD", on = !liveMode, focused = active && topIndex == 1)
        Spacer(Modifier.width(1.dp).weight(1f))
        Box(
            modifier = Modifier
                .width(300.dp)
                .then(
                    if (active && topIndex == 2)
                        Modifier.clip(RoundedCornerShape(12.dp)).border(2.dp, palette.primary, RoundedCornerShape(12.dp))
                    else Modifier
                )
        ) {
            SearchBox(
                query = searchQuery,
                hint = "Search movies & series",
                onChange = onSearchChange,
                onFocusChanged = onSearchFocusChanged,
                focusRequester = searchFocusRequester
            )
        }
        Spacer(Modifier.width(14.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(if (active && topIndex == 3) palette.primary else Color.Black.copy(alpha = 0.45f))
                .then(if (active && topIndex == 3) Modifier else Modifier.border(1.dp, palette.text.copy(alpha = 0.18f), RoundedCornerShape(24.dp)))
                .onTap("settings") { onSettings() }
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Settings, null,
                tint = if (active && topIndex == 3) palette.background else palette.text,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Settings",
                color = if (active && topIndex == 3) palette.background else palette.text,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** A Live TV / VOD toggle chip. [on] = this is the active mode (filled); [focused] = D-pad on it. */
@Composable
private fun ModeChip(label: String, on: Boolean, focused: Boolean) {
    val palette = appPalette
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (on) palette.primary else Color.Black.copy(alpha = 0.45f))
            .then(if (focused) Modifier.border(2.dp, palette.text, RoundedCornerShape(22.dp)) else Modifier)
            .padding(horizontal = 20.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = if (on) palette.background else palette.text,
            fontSize = 14.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HeroDetails(item: VodItem, resume: Boolean = false) {
    val palette = appPalette
    Column(
        modifier = Modifier
            .padding(start = EDGE, end = EDGE, bottom = 14.dp)
            .fillMaxWidth(0.62f)
    ) {
        Text(
            item.name,
            color = palette.text, fontSize = 34.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.ratingImdb > 0.0) {
                Icon(Icons.Filled.Star, null, tint = Color(0xFFF2C14E), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(String.format("%.1f", item.ratingImdb), color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
            }
            item.year.takeIf { it.isNotBlank() }?.let {
                Text(it, color = palette.text.copy(alpha = 0.85f), fontSize = 13.sp); Spacer(Modifier.width(12.dp))
            }
            item.genre.takeIf { it.isNotBlank() }?.let {
                Text(it, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(12.dp))
            }
            HeroChip(if (item.isSeries) "SERIES" else "MOVIE")
            if (item.isHd) { Spacer(Modifier.width(8.dp)); HeroChip("HD") }
        }
        if (item.description.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                item.description,
                color = palette.text.copy(alpha = 0.78f), fontSize = 14.sp, lineHeight = 19.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(14.dp))
        PlayCta(
            when {
                resume -> "Resume · OK"
                item.isSeries -> "Watch · OK"
                else -> "Play · OK"
            }
        )
    }
}

@Composable
private fun HeroChannelDetails(channel: Channel) {
    val palette = appPalette
    Column(
        modifier = Modifier
            .padding(start = EDGE, end = EDGE, bottom = 14.dp)
            .fillMaxWidth(0.62f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(palette.liveDot))
            Spacer(Modifier.width(7.dp))
            Text("LIVE", color = palette.liveDot, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (channel.number.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Text("CH ${channel.number}", color = palette.textSecondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            channel.name,
            color = palette.text, fontSize = 34.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (channel.nowPlaying.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Now: ${channel.nowPlaying}",
                color = palette.text.copy(alpha = 0.8f), fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(14.dp))
        PlayCta("Watch · OK")
    }
}

@Composable
private fun PlayCta(label: String) {
    val palette = appPalette
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.primary)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.PlayArrow, null, tint = palette.background, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = palette.background, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeroChip(text: String) {
    val palette = appPalette
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, palette.text.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, color = palette.text.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RailsList(
    rows: List<CinematicRail>,
    railListState: androidx.compose.foundation.lazy.LazyListState,
    activeRail: Int,
    activeItem: Int,
    railsActive: Boolean,
    onFocusRailItem: (Int, Int) -> Unit,
    onOpenRailItem: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = appPalette
    Box(modifier) {
        LazyColumn(
            state = railListState,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(rows, key = { _, r -> "${r.isLive}:${r.id}" }) { rIdx, rail ->
                RailRow(
                    rail = rail,
                    active = railsActive && rIdx == activeRail,
                    activeItem = if (rIdx == activeRail) activeItem else -1,
                    onFocusItem = { i -> onFocusRailItem(rIdx, i) },
                    onOpenItem = { i -> onOpenRailItem(rIdx, i) }
                )
            }
        }
        // Cards that are only half scrolled into view get chopped off flat against the screen edge,
        // which reads as a rendering bug. Fading both margins turns that into a deliberate edge.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(EDGE)
                .background(
                    Brush.horizontalGradient(
                        0f to palette.background,
                        1f to Color.Transparent
                    )
                )
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(EDGE)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        1f to palette.background
                    )
                )
        )
    }
}

@Composable
private fun RailRow(
    rail: CinematicRail,
    active: Boolean,
    activeItem: Int,
    onFocusItem: (Int) -> Unit,
    onOpenItem: (Int) -> Unit
) {
    val palette = appPalette
    // CHILD LOCK — an adult genre/category rail marks its heading with a lock.
    val railLocked = LocalParentalGate.current.isRestricted(rail.title)
    val rowState = rememberLazyListState()
    LaunchedEffect(active, activeItem) {
        if (active && activeItem >= 0) rowState.snapOrAnimateTo(activeItem.coerceAtLeast(0))
    }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = EDGE, bottom = 10.dp)
        ) {
            if (rail.isLive) {
                Icon(
                    Icons.Filled.LiveTv, null,
                    tint = if (active) palette.text else palette.textSecondary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                rail.title,
                color = if (active) palette.text else palette.textSecondary,
                fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            if (railLocked) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Lock, null, tint = palette.primary, modifier = Modifier.size(15.dp))
            }
        }
        when {
            // Loading placeholder while the rail's first page is fetched.
            !rail.loaded && rail.size == 0 -> Row(
                Modifier.padding(start = EDGE, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Loading…", color = palette.textSecondary, fontSize = 13.sp)
            }
            rail.size == 0 -> Text(
                "No items", color = palette.textSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(start = EDGE, bottom = 6.dp)
            )
            else -> LazyRow(
                state = rowState,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = EDGE)
            ) {
                if (rail.isLive) {
                    itemsIndexed(rail.channels, key = { _, it -> it.id }) { i, ch ->
                        ChannelCard(ch, active && i == activeItem, { onFocusItem(i) }, { onOpenItem(i) })
                    }
                } else {
                    itemsIndexed(rail.vod, key = { _, it -> it.id }) { i, item ->
                        PosterCard(
                            item = item,
                            selected = active && i == activeItem,
                            // Only the Continue-watching rail carries a progress bar.
                            progress = rail.progressByCmd[item.cmd] ?: 0f,
                            onTap = { onFocusItem(i) },
                            onOpen = { onOpenItem(i) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, selected: Boolean, onTap: () -> Unit, onOpen: () -> Unit) {
    val palette = appPalette
    val shape = RoundedCornerShape(10.dp)
    Column(modifier = Modifier.width(210.dp).padding(vertical = 16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .premiumFocus(selected, shape, glow = palette.primary, scaleTo = 1.06f, lift = 14)
                .clip(shape)
                .background(palette.surface)
                .then(if (selected) Modifier.border(3.dp, palette.text, shape) else Modifier)
                .onTap("cz-ch-${channel.id}") { if (selected) onOpen() else onTap() },
            contentAlignment = Alignment.Center
        ) {
            if (channel.logoUrl != null) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(18.dp)
                )
            } else {
                Text(channel.name.take(2).uppercase(), color = palette.primary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.align(Alignment.TopStart).padding(8.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(palette.liveDot))
                    Spacer(Modifier.width(4.dp))
                    Text("LIVE", color = palette.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(7.dp)
                        .size(28.dp).clip(CircleShape).background(palette.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = palette.background, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            if (channel.number.isNotBlank()) "${channel.number}  ${channel.name}" else channel.name,
            color = if (selected) palette.text else palette.textSecondary,
            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PosterCard(
    item: VodItem,
    selected: Boolean,
    onTap: () -> Unit,
    onOpen: () -> Unit,
    progress: Float = 0f
) {
    val palette = appPalette
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier.width(158.dp).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_RATIO)
                .premiumFocus(selected, shape, glow = palette.primary, scaleTo = 1.14f, lift = 24)
                .clip(shape)
                .background(palette.surface)
                .then(if (selected) Modifier.border(3.dp, palette.text, shape) else Modifier)
                .onTap("cz-${item.id}") { if (selected) onOpen() else onTap() }
        ) {
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(item.name.take(2).uppercase(), color = palette.primary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (item.isHd) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd).padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) { Text("HD", color = palette.text, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
            if (selected) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))
                    )
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd).padding(7.dp)
                        .size(28.dp).clip(CircleShape)
                        .background(palette.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = palette.background, modifier = Modifier.size(16.dp))
                }
            }
            // "Continue watching" — how far in this title already is, pinned to the poster's foot.
            if (progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.28f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(palette.primary)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Only the focused tile is captioned (Netflix-style); the rest stay pure artwork.
        Text(
            if (selected) item.name else "",
            color = palette.text,
            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
