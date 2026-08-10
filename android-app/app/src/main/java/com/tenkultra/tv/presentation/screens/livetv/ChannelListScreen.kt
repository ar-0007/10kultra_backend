package com.tenkultra.tv.presentation.screens.livetv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tenkultra.tv.domain.model.Channel
import com.tenkultra.tv.domain.model.EpgProgram
import com.tenkultra.tv.presentation.common.LoadingDots
import com.tenkultra.tv.presentation.common.RemoteColor
import com.tenkultra.tv.presentation.common.RemoteKeyBus
import com.tenkultra.tv.presentation.common.SearchBox
import com.tenkultra.tv.presentation.common.onTap
import com.tenkultra.tv.presentation.common.premiumFocus
import com.tenkultra.tv.presentation.theme.appPalette
import com.tenkultra.tv.presentation.theme.classicRowBrush
import com.tenkultra.tv.presentation.theme.screenBackgroundBrush

@OptIn(UnstableApi::class)
@Composable
fun ChannelListScreen(
    onBack: () -> Unit,
    onPlay: (Channel) -> Unit,
    viewModel: ChannelListViewModel = hiltViewModel()
) {
    val palette = appPalette
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val searchFieldFocus = remember { FocusRequester() }
    // Saveable so returning from the player restores the channel you were on (not back to top).
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var guideActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    // Search box is SELECTED (highlighted) but the keyboard is NOT open yet — it only opens on OK.
    var searchHighlighted by remember { mutableStateOf(false) }
    // FAVORITES view (green button) shows only starred channels; MOVE mode (blue button)
    // reorders them with Up/Down. Both persist across the player round-trip.
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var moveMode by rememberSaveable { mutableStateOf(false) }
    // Two-step OK with a STICKY preview: the FIRST OK on a channel starts its preview in the side
    // box, and that preview KEEPS PLAYING while you move the highlight up/down — it only switches
    // when you press OK on a DIFFERENT channel. A SECOND OK on the channel you're already
    // previewing opens it fullscreen. previewIndex = the channel now in the preview (-1 = none yet,
    // so landing on the list makes no create_link calls until the user asks).
    var previewIndex by rememberSaveable { mutableIntStateOf(-1) }

    // Source list: all loaded channels, or just the user's favourites (in their chosen order).
    val baseList = if (favoritesOnly) state.favorites else state.items
    // Client-side filter (this portal ignores itv search), over the chosen source.
    val items = if (query.isBlank()) baseList
    else baseList.filter { it.name.contains(query, true) || it.number.contains(query) }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Returning from the full player: move the highlight to whatever channel the user was last
    // watching (they may have surfed with ▲▼ inside the player), so the placeholder lands on the
    // channel they backed out of — not the one they originally opened.
    // When we jump straight to the surfed channel (return from the full player), the list must
    // land there STATICALLY — no visible animated scroll from the top. This flag makes that ONE
    // scroll instant; normal ▲▼ stepping keeps the smooth animated scroll.
    var jumpInstant by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(items.size) {
        viewModel.consumeReturnIndex()?.let { idx ->
            val clamped = idx.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            jumpInstant = true          // set BEFORE the index so the scroll effect sees it
            selectedIndex = clamped
            previewIndex = clamped
        }
    }
    LaunchedEffect(selectedIndex) { viewModel.onSelectionChanged(selectedIndex) }
    // Keep the highlighted channel's stream link (and its immediate neighbours) warm as the user
    // browses, so pressing OK to preview it is an instant cache hit instead of a fresh ~0.5s
    // create_link. Warmed on every settle (debounced in the VM), even before the first preview.
    LaunchedEffect(selectedIndex) {
        if (items.isNotEmpty()) viewModel.prefetchPreviewWindow(items, selectedIndex)
    }
    LaunchedEffect(items.size) {
        if (selectedIndex > items.lastIndex) selectedIndex = items.lastIndex.coerceAtLeast(0)
    }

    val selected = items.getOrNull(selectedIndex)
    // The channel currently shown in the side preview (only changes on OK, not on highlight move).
    val previewChannel = items.getOrNull(previewIndex)
    val currentPage = if (state.maxPageItems > 0) (selectedIndex / state.maxPageItems) + 1 else 1

    // While the TV-guide panel is open, load EPG for whichever channel is highlighted.
    LaunchedEffect(selectedIndex, guideActive) {
        if (guideActive) selected?.let(viewModel::loadEpg)
    }

    // Colour-button actions live here in ONE place. The physical colour keys arrive via
    // RemoteKeyBus (caught at the Activity, so focus can't eat them); the on-screen coloured
    // hints below call the same lambdas. Semantics unchanged: RED play, GREEN favourites filter,
    // YELLOW add/remove favourite, BLUE toggle move-mode.
    val onColor by rememberUpdatedState<(RemoteColor) -> Unit> { color ->
        when (color) {
            RemoteColor.RED -> selected?.let { viewModel.prepareLivePlayback(items, selectedIndex, canPage = query.isBlank() && !favoritesOnly); onPlay(it) }
            RemoteColor.GREEN -> { favoritesOnly = !favoritesOnly; moveMode = false; selectedIndex = 0; previewIndex = -1 }
            RemoteColor.YELLOW -> selected?.let { viewModel.toggleFavorite(it) }
            RemoteColor.BLUE -> if (favoritesOnly && items.isNotEmpty()) moveMode = !moveMode
        }
    }
    LaunchedEffect(Unit) { RemoteKeyBus.events.collect { onColor(it) } }

    // System Back (reliable even if D-pad focus is on the search field).
    BackHandler { if (guideActive) guideActive = false else onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBackgroundBrush(palette))
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                // While TYPING (field has focus + keyboard open): Down/Back close the keyboard and
                // return to the list-highlight (Back keeps the box highlighted, Down enters the list).
                if (searchFocused) {
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionDown -> { searchHighlighted = false; focus.requestFocus(); return@onPreviewKeyEvent true }
                            Key.Back -> { searchHighlighted = true; focus.requestFocus(); return@onPreviewKeyEvent true }
                        }
                    }
                    return@onPreviewKeyEvent false // let the field type
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Search box SELECTED but not typing: OK opens the keyboard; Down goes back to list.
                if (searchHighlighted) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { searchFieldFocus.requestFocus(); return@onPreviewKeyEvent true }
                        Key.DirectionDown -> { searchHighlighted = false; return@onPreviewKeyEvent true }
                        Key.Back -> { searchHighlighted = false; return@onPreviewKeyEvent true }
                        Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight -> return@onPreviewKeyEvent true // stay on search
                        else -> {}
                    }
                }
                when (event.key) {
                    // In MOVE mode, Up/Down REORDER the highlighted favourite (selection follows it).
                    // Otherwise Up navigates, and at the top HIGHLIGHTS the search box (no keyboard yet).
                    Key.DirectionUp -> {
                        if (moveMode && favoritesOnly && query.isBlank()) {
                            if (selectedIndex > 0) { viewModel.moveFavorite(selectedIndex, -1); selectedIndex-- }
                        } else if (selectedIndex > 0) selectedIndex-- else searchHighlighted = true
                        true
                    }
                    Key.DirectionDown -> {
                        if (moveMode && favoritesOnly && query.isBlank()) {
                            if (selectedIndex < items.lastIndex) { viewModel.moveFavorite(selectedIndex, +1); selectedIndex++ }
                        } else if (selectedIndex < items.lastIndex) selectedIndex++
                        true
                    }
                    // RIGHT opens the TV GUIDE (EPG); LEFT closes it.
                    Key.DirectionRight -> { if (items.isNotEmpty()) guideActive = true; true }
                    Key.DirectionLeft -> { guideActive = false; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        // First OK on a channel switches the preview to it; a SECOND OK on the
                        // channel already in the preview opens it fullscreen. Moving the highlight
                        // does NOT touch previewIndex, so the old preview keeps playing until then.
                        if (previewIndex == selectedIndex) {
                            selected?.let { viewModel.prepareLivePlayback(items, selectedIndex, canPage = query.isBlank() && !favoritesOnly); onPlay(it) }
                        } else {
                            previewIndex = selectedIndex
                        }
                        true
                    }
                    // Colour remote buttons (RED/GREEN/YELLOW/BLUE) are handled via RemoteKeyBus,
                    // caught at MainActivity.dispatchKeyEvent so focus can't swallow them.
                    Key.Back -> {
                        when {
                            guideActive -> { guideActive = false; true }
                            moveMode -> { moveMode = false; true }
                            favoritesOnly -> { favoritesOnly = false; selectedIndex = 0; true }
                            else -> { onBack(); true }
                        }
                    }
                    else -> false
                }
            }
            .padding(16.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Live TV", color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("  /  ", color = palette.textSecondary, fontSize = 18.sp)
                Text(
                    if (favoritesOnly) "FAVORITES" else state.genreTitle.uppercase(),
                    color = palette.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (moveMode) {
                    Text("   ↕ MOVE MODE", color = Color(0xFF42A5F5), fontSize = 14.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            SearchBox(
                query = query,
                hint = "Search channels…  (press ▲, then OK to type)",
                onChange = { query = it; selectedIndex = 0 },
                onFocusChanged = { searchFocused = it; if (it) searchHighlighted = true },
                focusRequester = searchFieldFocus,
                highlighted = searchHighlighted
            )
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth().weight(1f)) {
                SideTab("BACK", active = false)
                Spacer(Modifier.width(6.dp))
                // The channel list is a narrow column (only as wide as it needs for the names) so
                // the live preview beside it gets the bulk of the screen.
                Box(Modifier.width(470.dp).fillMaxHeight()) {
                    ChannelList(
                        items = items,
                        selectedIndex = selectedIndex,
                        jumpInstant = jumpInstant,
                        onJumpConsumed = { jumpInstant = false },
                        loadingMore = state.loadingMore,
                        favoriteIds = state.favoriteIds,
                        moveMode = moveMode,
                        onItemTap = { i ->
                            // Match the D-pad two-step: first tap selects + previews the channel,
                            // a second tap on the channel already previewing opens it fullscreen.
                            if (previewIndex == i) {
                                items.getOrNull(i)?.let {
                                    viewModel.prepareLivePlayback(items, i, canPage = query.isBlank() && !favoritesOnly)
                                    onPlay(it)
                                }
                            } else {
                                selectedIndex = i; previewIndex = i
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (favoritesOnly && items.isEmpty() && !state.loading) {
                        Text(
                            "No favourites yet.\nHighlight a channel and press the YELLOW button (★) to add it.",
                            color = palette.textSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                if (guideActive) {
                    TvGuidePanel(
                        channel = selected,
                        epg = state.epg,
                        loading = state.epgLoading,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                } else {
                    ChannelDetail(
                        // The preview panel represents the channel you're PREVIEWING (last OK'd),
                        // not the highlighted one — so it keeps playing while you browse. Before
                        // any OK it idles on the highlighted channel's logo.
                        channel = previewChannel ?: selected,
                        previewEnabled = previewChannel != null,
                        resolvePreview = viewModel::resolvePreviewUrl,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Spacer(Modifier.width(6.dp))
                SideTab("TV GUIDE", active = guideActive)
            }

            Text(
                text = "Page $currentPage of ${state.totalPages}. Found ${state.totalItems} CHANNELS.",
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isFav = selected != null && selected.id in state.favoriteIds
                Hint("Play", Color(0xFFE53935), Modifier.weight(1f).onTap("play") {
                    selected?.let { viewModel.prepareLivePlayback(items, selectedIndex, canPage = query.isBlank() && !favoritesOnly); onPlay(it) }
                })
                Hint(
                    if (favoritesOnly) "All channels" else "Favorites",
                    Color(0xFF43A047),
                    Modifier.weight(1f).onTap("favfilter") {
                        favoritesOnly = !favoritesOnly; moveMode = false; selectedIndex = 0; previewIndex = -1
                    },
                    active = favoritesOnly
                )
                Hint(
                    if (isFav) "Remove ★" else "Add ★",
                    Color(0xFFFBC02D),
                    Modifier.weight(1f).onTap("favtoggle") { selected?.let { viewModel.toggleFavorite(it) } }
                )
                Hint(
                    if (moveMode) "Done" else "Move",
                    Color(0xFF1E88E5),
                    Modifier.weight(1f).onTap("move") {
                        if (favoritesOnly && items.isNotEmpty()) moveMode = !moveMode
                    },
                    active = moveMode,
                    enabled = favoritesOnly
                )
            }
        }

        if (state.loading) {
            LoadingDots(modifier = Modifier.align(Alignment.Center))
        }
        state.error?.let {
            Text(it, color = Color(0xFFFF8A80), modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun ChannelList(
    items: List<Channel>,
    selectedIndex: Int,
    jumpInstant: Boolean,
    onJumpConsumed: () -> Unit,
    loadingMore: Boolean,
    favoriteIds: Set<String>,
    moveMode: Boolean,
    onItemTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = appPalette
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        val target = (selectedIndex - 5).coerceAtLeast(0)
        // Return-from-player jump → snap instantly (static, no visible scroll); else animate.
        if (jumpInstant) { listState.scrollToItem(target); onJumpConsumed() }
        else listState.animateScrollToItem(target)
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
    ) {
        itemsIndexed(items, key = { index, channel -> "${channel.id}-$index" }) { index, channel ->
            ChannelRow(
                channel = channel,
                selected = index == selectedIndex,
                isFavorite = channel.id in favoriteIds,
                moveActive = moveMode && index == selectedIndex,
                modifier = Modifier.onTap(index) { onItemTap(index) }
            )
        }
        if (loadingMore) {
            item {
                Text("Loading more…", color = palette.textSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    selected: Boolean,
    isFavorite: Boolean,
    moveActive: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = appPalette
    val textColor = if (selected) palette.background else palette.text
    val shape = RoundedCornerShape(8.dp)
    // Tall rows with BIG BOLD text — easy to read from across the room (elderly viewers). The
    // list column is narrow so these wide, tall rows still leave the preview the bulk of the
    // screen. The selected row grows bigger for a clear highlight. Animated for a smooth feel.
    val rowHeight by animateDpAsState(if (selected) 68.dp else 46.dp, tween(180), label = "rowH")
    val nameSize by animateFloatAsState(if (selected) 29f else 24f, tween(180), label = "nameSz")
    val numSize by animateFloatAsState(if (selected) 25f else 21f, tween(180), label = "numSz")
    // In move-mode the highlighted row gets a blue border/glow to signal it's being dragged.
    val borderColor = if (moveActive) Color(0xFF42A5F5) else palette.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .premiumFocus(selected, shape, glow = borderColor, scaleTo = 1.045f, lift = 10)
            .clip(shape)
            .background(classicRowBrush(palette, selected))
            .then(
                if (selected) Modifier.border(if (moveActive) 2.dp else 1.dp, borderColor, shape)
                else Modifier
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel number in its own bordered box — one line, clearly separated from the name.
        Box(
            modifier = Modifier
                .width(72.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.primary.copy(alpha = 0.12f))
                .border(1.5.dp, palette.primary, RoundedCornerShape(6.dp))
                .padding(vertical = 5.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channel.number.ifBlank { "—" },
                color = palette.primary,
                fontSize = numSize.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = channel.name,
            color = textColor,
            fontSize = nameSize.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (moveActive) {
            Text("↕", color = if (selected) palette.background else Color(0xFF42A5F5),
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
        }
        if (isFavorite) {
            Text("★", color = Color(0xFFFFC107), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
        }
        if (channel.isHd) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (selected) palette.background else palette.primary)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text("HD", color = if (selected) palette.primary else palette.background,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ChannelDetail(
    channel: Channel?,
    previewEnabled: Boolean,
    resolvePreview: suspend (Channel, Boolean) -> String?,
    modifier: Modifier = Modifier
) {
    val palette = appPalette
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // DUAL muted preview players ("double buffer") — the previewed channel NEVER pauses while the
    // next one loads. FRONT keeps playing in the window; BACK (no surface bound) resolves +
    // buffers the newly OK'd channel silently, and swaps in only when READY. Dead cached links
    // fail invisibly on BACK and retry fresh — the viewer keeps watching the old channel.
    fun buildPreviewPlayer(): ExoPlayer {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Lavf/57.83.100")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
        // Fast-start tuning: a tiny start buffer so the preview shows a frame ASAP instead of
        // waiting to fill a big cushion — a browsing preview only needs to peek.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(500, 6_000, 150, 300)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                volume = 0f            // muted preview
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }
    val previewA = remember { buildPreviewPlayer() }
    val previewB = remember { buildPreviewPlayer() }
    var frontIsA by remember { mutableStateOf(true) }
    val previewPlayer = if (frontIsA) previewA else previewB

    DisposableEffect(Unit) {
        onDispose {
            runCatching { previewA.release() }
            runCatching { previewB.release() }
        }
    }

    // Pause the preview when the screen goes to the background / opens the full player, so it
    // isn't wasting bandwidth playing hidden; resume when we come back to the list. BOTH players
    // are resumed — after a swap the front may be either one (resuming just the composition-time
    // capture left the visible preview frozen — the backgrounding bug from the review).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> { previewA.pause(); previewB.pause() }
                Lifecycle.Event.ON_RESUME -> { previewA.play(); previewB.play() }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // SEAMLESS preview switch: prepare the new channel on the hidden BACK player while the FRONT
    // keeps playing the old one; swap only when the new channel is READY. A dead/stale link
    // errors on BACK (invisible) and retries with a FRESH link.
    LaunchedEffect(channel?.id, previewEnabled) {
        val ch = channel
        if (ch == null || !previewEnabled) {
            // Nothing to preview (before the first OK, or leaving the list) → clear the window.
            previewA.stop(); previewA.clearMediaItems()
            previewB.stop(); previewB.clearMediaItems()
            return@LaunchedEffect
        }
        delay(60)           // tiny debounce so fast scrolling doesn't spam create_link
        val started = System.currentTimeMillis()
        var attempt = 0
        while (attempt < 3) {
            // First try reuses the warmed/cached link (instant); a retry forces a fresh one.
            val url = runCatching { resolvePreview(ch, attempt > 0) }.getOrNull()
            android.util.Log.d("UltraPrev", "ch=${ch.name} attempt=$attempt url=${url?.takeLast(40)}")
            if (url.isNullOrBlank()) { attempt++; delay(300); continue }
            val back = if (frontIsA) previewB else previewA
            back.setMediaItem(MediaItem.fromUri(url))
            back.prepare()
            // Wait for the new channel to be READY (→ swap) or FAIL (→ fresh retry). We don't
            // retry on a slow buffer — still-buffering isn't a failure, and the old channel keeps
            // playing meanwhile. The coroutine is cancelled if the previewed channel changes.
            var deadLink = false
            while (!deadLink) {
                if (back.playbackState == Player.STATE_READY) {
                    val old = if (frontIsA) previewA else previewB
                    frontIsA = !frontIsA          // swap: READY back player becomes the window
                    old.stop(); old.clearMediaItems()
                    android.util.Log.d(
                        "UltraPrev",
                        "ch=${ch.name} SWAPPED in ${System.currentTimeMillis() - started}ms (attempt=$attempt)"
                    )
                    return@LaunchedEffect
                }
                val err = back.playerError
                if (err != null) {
                    android.util.Log.d("UltraPrev", "ch=${ch.name} attempt=$attempt ERROR ${err.errorCodeName}")
                    runCatching { back.stop() }
                    deadLink = true               // dead link → fresh retry
                } else {
                    delay(150)
                }
            }
            attempt++
        }
        android.util.Log.d("UltraPrev", "ch=${ch.name} FAILED after $attempt attempts")
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)   // big 16:9 preview window (scales with the wider panel)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black), // switching shows plain black — never a spinner
            contentAlignment = Alignment.Center
        ) {
            // The live muted video fills the window once it's ready…
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        // KEEP the current channel's last frame on screen until the NEW channel
                        // renders — no black flash between channels (seamless switch like a real STB).
                        setKeepContentOnPlayerReset(true)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        player = previewPlayer
                    }
                },
                // Re-bind to whichever preview player is FRONT after each seamless swap.
                update = { view -> if (view.player !== previewPlayer) view.player = previewPlayer },
                modifier = Modifier.fillMaxSize()
            )
            // Before the user presses OK (preview not armed) show the logo / channel initials so
            // the window isn't blank. Once a preview IS armed, the old channel keeps playing (or
            // its last frame holds) while the next one loads — no spinner, no black flash.
            if (!previewEnabled) {
                if (channel?.logoUrl != null) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.size(160.dp)
                    )
                } else {
                    Text(
                        channel?.name?.take(2)?.uppercase() ?: "TV",
                        color = palette.primary, fontSize = 48.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = channel?.name.orEmpty(),
            color = palette.text, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Now: ${channel?.nowPlaying.orEmpty().ifEmpty { "—" }}",
            color = palette.textSecondary, fontSize = 16.sp
        )
    }
}

@Composable
private fun Hint(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true
) {
    val palette = appPalette
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .background(if (active) color.copy(alpha = 0.28f) else palette.surface)
            .then(if (active) Modifier.border(1.dp, color, shape) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(if (enabled) color else color.copy(alpha = 0.4f)))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (enabled) palette.text else palette.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Vertical side tab (BACK / TV GUIDE) — letters stacked, highlighted when active. */
@Composable
private fun SideTab(label: String, active: Boolean) {
    val palette = appPalette
    Column(
        modifier = Modifier
            .width(26.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) palette.primary else palette.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        label.forEach { ch ->
            if (ch == ' ') Spacer(Modifier.height(6.dp))
            else Text(
                ch.toString(),
                color = if (active) palette.background else palette.text,
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/** The TV GUIDE (EPG) panel: channel header + the short-EPG schedule (time · program). */
@Composable
private fun TvGuidePanel(
    channel: Channel?,
    epg: List<EpgProgram>,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = appPalette
    Column(modifier = modifier) {
        Text(
            channel?.name.orEmpty().ifEmpty { "—" },
            color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text("TV GUIDE", color = palette.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        when {
            loading -> Text("Loading guide…", color = palette.textSecondary, fontSize = 14.sp)
            epg.isEmpty() -> Text("No program details available.", color = palette.textSecondary, fontSize = 14.sp)
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(epg) { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.surface.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            p.time, color = palette.primary, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.width(58.dp)
                        )
                        Text(
                            p.title, color = palette.text, fontSize = 13.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
