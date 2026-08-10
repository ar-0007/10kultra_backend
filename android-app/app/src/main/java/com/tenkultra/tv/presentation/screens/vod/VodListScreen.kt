package com.tenkultra.tv.presentation.screens.vod

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import com.tenkultra.tv.domain.model.VodItem
import com.tenkultra.tv.presentation.common.LoadingDots
import com.tenkultra.tv.presentation.common.RemoteColor
import com.tenkultra.tv.presentation.common.RemoteKeyBus
import com.tenkultra.tv.presentation.common.SearchBox
import com.tenkultra.tv.presentation.common.onTap
import com.tenkultra.tv.presentation.common.premiumFocus
import com.tenkultra.tv.presentation.theme.appPalette
import com.tenkultra.tv.presentation.theme.classicRowBrush
import com.tenkultra.tv.presentation.theme.screenBackgroundBrush
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun VodListScreen(
    onBack: () -> Unit,
    onSelect: (VodItem, String) -> Unit = { _, _ -> },
    viewModel: VodListViewModel = hiltViewModel()
) {
    val palette = appPalette
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val searchFieldFocus = remember { FocusRequester() }
    // Saveable so returning from the player/detail restores the row you were on (not back to top).
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var aboutActive by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    // Search box SELECTED (highlighted) but keyboard NOT open — opens only on OK.
    var searchHighlighted by remember { mutableStateOf(false) }
    // FAVORITES view (green button) shows only starred titles; MOVE mode (blue button) reorders
    // them with Up/Down. Same behaviour as Live TV. Both persist across the detail/player round-trip.
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var moveMode by rememberSaveable { mutableStateOf(false) }

    // Source list: the loaded category, or just the user's favourites (in their chosen order).
    // In favourites view the search box filters client-side (the server search only applies to
    // the browsable category).
    val baseList = if (favoritesOnly) state.favorites else state.items
    val items = if (favoritesOnly && state.query.isNotBlank())
        baseList.filter { it.name.contains(state.query.trim(), ignoreCase = true) }
    else baseList

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(selectedIndex) { if (!favoritesOnly) viewModel.onSelectionChanged(selectedIndex) }
    LaunchedEffect(items.size) {
        if (selectedIndex > items.lastIndex) selectedIndex = items.lastIndex.coerceAtLeast(0)
    }

    val selected = items.getOrNull(selectedIndex)
    val currentPage = if (state.maxPageItems > 0) (selectedIndex / state.maxPageItems) + 1 else 1

    // Colour-button actions in ONE place. Physical colour keys arrive via RemoteKeyBus (caught at
    // the Activity so focus can't eat them); the on-screen coloured hints call the same lambdas.
    // Same semantics as Live TV: RED open, GREEN favourites filter, YELLOW add/remove favourite,
    // BLUE toggle move-mode.
    val onColor by rememberUpdatedState<(RemoteColor) -> Unit> { color ->
        when (color) {
            RemoteColor.RED -> selected?.let { viewModel.prepareVodPlayback(it); onSelect(it, state.categoryId) }
            RemoteColor.GREEN -> { favoritesOnly = !favoritesOnly; moveMode = false; selectedIndex = 0 }
            RemoteColor.YELLOW -> selected?.let { viewModel.toggleFavorite(it) }
            RemoteColor.BLUE -> if (favoritesOnly && items.isNotEmpty()) moveMode = !moveMode
        }
    }
    LaunchedEffect(Unit) { RemoteKeyBus.events.collect { onColor(it) } }

    // System Back (reliable even if D-pad focus is on the search field).
    BackHandler {
        when {
            aboutActive -> aboutActive = false
            moveMode -> moveMode = false
            favoritesOnly -> { favoritesOnly = false; selectedIndex = 0 }
            else -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBackgroundBrush(palette))
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                // While TYPING (keyboard open): Down enters the list, Back keeps the box highlighted.
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
                        if (moveMode && favoritesOnly && state.query.isBlank()) {
                            if (selectedIndex > 0) { viewModel.moveFavorite(selectedIndex, -1); selectedIndex-- }
                        } else if (selectedIndex > 0) selectedIndex-- else searchHighlighted = true
                        true
                    }
                    Key.DirectionDown -> {
                        if (moveMode && favoritesOnly && state.query.isBlank()) {
                            if (selectedIndex < items.lastIndex) { viewModel.moveFavorite(selectedIndex, +1); selectedIndex++ }
                        } else if (selectedIndex < items.lastIndex) selectedIndex++
                        true
                    }
                    // RIGHT opens the ABOUT MOVIE description; LEFT closes it.
                    Key.DirectionRight -> { if (selected != null) aboutActive = true; true }
                    Key.DirectionLeft -> { aboutActive = false; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        selected?.let { viewModel.prepareVodPlayback(it); onSelect(it, state.categoryId) }; true
                    }
                    Key.Back -> {
                        when {
                            aboutActive -> { aboutActive = false; true }
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
            Breadcrumb(category = state.categoryTitle, favoritesOnly = favoritesOnly, moveMode = moveMode)
            Spacer(Modifier.height(8.dp))
            SearchBox(
                query = state.query,
                hint = "Search movies & series…  (press ▲, then OK to type)",
                onChange = viewModel::setQuery,
                onFocusChanged = { searchFocused = it; if (it) searchHighlighted = true },
                focusRequester = searchFieldFocus,
                highlighted = searchHighlighted
            )
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth().weight(1f)) {
                VerticalTab("BACK")
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    VodList(
                        items = items,
                        selectedIndex = selectedIndex,
                        loadingMore = state.loadingMore && !favoritesOnly,
                        favoriteIds = state.favoriteIds,
                        moveMode = moveMode,
                        onItemTap = { i ->
                            selectedIndex = i
                            items.getOrNull(i)?.let { viewModel.prepareVodPlayback(it); onSelect(it, state.categoryId) }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (favoritesOnly && items.isEmpty() && !state.loading) {
                        Text(
                            "No favourites yet.\nHighlight a title and press the YELLOW button (★) to add it.",
                            color = palette.textSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                DetailPanel(
                    item = selected,
                    showDescription = aboutActive,
                    modifier = Modifier.width(320.dp).fillMaxHeight()
                )
                Spacer(Modifier.width(6.dp))
                VerticalTab("ABOUT MOVIE", active = aboutActive)
            }

            Text(
                text = if (favoritesOnly) "Found ${items.size} FAVOURITES."
                else "Page $currentPage of ${state.totalPages}. Found ${state.totalItems} RECORDINGS.",
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            val isFav = selected != null && selected.id in state.favoriteIds
            ColorButtonsBar(
                favoritesOnly = favoritesOnly,
                isFav = isFav,
                moveMode = moveMode,
                onPlay = { selected?.let { viewModel.prepareVodPlayback(it); onSelect(it, state.categoryId) } },
                onToggleFavoritesView = { favoritesOnly = !favoritesOnly; moveMode = false; selectedIndex = 0 },
                onToggleFavorite = { selected?.let { viewModel.toggleFavorite(it) } },
                onToggleMove = { if (favoritesOnly && items.isNotEmpty()) moveMode = !moveMode }
            )
        }

        if (state.loading && !favoritesOnly) {
            LoadingDots(modifier = Modifier.align(Alignment.Center))
        }
        if (!favoritesOnly) {
            state.error?.let {
                Text(it, color = Color(0xFFFF8A80), modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun Breadcrumb(category: String, favoritesOnly: Boolean = false, moveMode: Boolean = false) {
    val palette = appPalette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Video club", color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("  /  ", color = palette.textSecondary, fontSize = 18.sp)
        Text(
            if (favoritesOnly) "FAVORITES" else category.uppercase(),
            color = palette.primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
        )
        if (!favoritesOnly) {
            Text("  /  ", color = palette.textSecondary, fontSize = 18.sp)
            Text("BY ADDTIME", color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("  /", color = palette.textSecondary, fontSize = 18.sp)
        }
        if (moveMode) {
            Text("   ↕ MOVE MODE", color = Color(0xFF42A5F5), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VodList(
    items: List<VodItem>,
    selectedIndex: Int,
    loadingMore: Boolean,
    favoriteIds: Set<String>,
    moveMode: Boolean,
    onItemTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = appPalette
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem((selectedIndex - 5).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        itemsIndexed(items, key = { index, item -> "${item.id}-$index" }) { index, item ->
            VodRow(
                item = item,
                selected = index == selectedIndex,
                isFavorite = item.id in favoriteIds,
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
private fun VodRow(
    item: VodItem,
    selected: Boolean,
    isFavorite: Boolean,
    moveActive: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = appPalette
    val textColor = if (selected) palette.background else palette.text
    val shape = RoundedCornerShape(8.dp)
    // Compact rows with BIG BOLD text — same easy-to-read style as Live TV, just a touch bigger
    // (movies/series titles are longer). The selected row grows for a clear highlight.
    val rowHeight by animateDpAsState(if (selected) 74.dp else 50.dp, tween(180), label = "vodH")
    val nameSize by animateFloatAsState(if (selected) 30f else 25f, tween(180), label = "vodSz")
    val dateSize by animateFloatAsState(if (selected) 16f else 13f, tween(180), label = "vodDt")
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
        if (item.isHd) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (selected) palette.background else palette.primary)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text("HD", color = if (selected) palette.primary else palette.background,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = item.name,
            color = textColor,
            fontSize = nameSize.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (moveActive) {
            Spacer(Modifier.width(6.dp))
            Text("↕", color = if (selected) palette.background else Color(0xFF42A5F5),
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (isFavorite) {
            Spacer(Modifier.width(6.dp))
            Text("★", color = Color(0xFFFFC107), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = monthYear(item.added),
            color = if (selected) palette.selected else palette.textSecondary,
            fontSize = dateSize.sp
        )
    }
}

@Composable
private fun DetailPanel(item: VodItem?, showDescription: Boolean = false, modifier: Modifier = Modifier) {
    val palette = appPalette
    Column(modifier = modifier) {
        Text("Genre: ${item?.genre.orEmpty().ifEmpty { "—" }}", color = palette.text, fontSize = 14.sp)
        Text(
            "Year: ${item?.year.orEmpty().ifEmpty { "—" }}   Duration: ${
                item?.durationMin.orEmpty().ifEmpty { "—" }
            } min.",
            color = palette.text, fontSize = 14.sp
        )
        Text("Director: ${item?.director.orEmpty().ifEmpty { "—" }}", color = palette.text, fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))
        if (showDescription) {
            // ABOUT MOVIE — full plot/description (RIGHT button).
            Text("ABOUT MOVIE", color = palette.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                item?.description?.trim().orEmpty().ifEmpty { "No description available for this title." },
                color = palette.textSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            )
        } else {
            Box(
                modifier = Modifier
                    .width(190.dp)
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surface),
                contentAlignment = Alignment.Center
            ) {
                if (item?.posterUrl != null) {
                    AsyncImage(model = item.posterUrl, contentDescription = item.name, modifier = Modifier.fillMaxSize())
                } else {
                    Text("No image", color = palette.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun VerticalTab(text: String, active: Boolean = false) {
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
        text.forEach { ch ->
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

@Composable
private fun ColorButtonsBar(
    favoritesOnly: Boolean,
    isFav: Boolean,
    moveMode: Boolean,
    onPlay: () -> Unit,
    onToggleFavoritesView: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleMove: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Same four colour actions as Live TV: RED play/open, GREEN favourites filter,
        // YELLOW add/remove ★, BLUE move-mode (only in the favourites view).
        ColorButton("Play", Color(0xFFE53935), Modifier.weight(1f).onTap("vodplay") { onPlay() })
        ColorButton(
            if (favoritesOnly) "All titles" else "Favorites",
            Color(0xFF43A047),
            Modifier.weight(1f).onTap("vodfavfilter") { onToggleFavoritesView() },
            active = favoritesOnly
        )
        ColorButton(
            if (isFav) "Remove ★" else "Add ★",
            Color(0xFFFBC02D),
            Modifier.weight(1f).onTap("vodfavtoggle") { onToggleFavorite() }
        )
        ColorButton(
            if (moveMode) "Done" else "Move",
            Color(0xFF1E88E5),
            Modifier.weight(1f).onTap("vodmove") { onToggleMove() },
            active = moveMode,
            enabled = favoritesOnly
        )
    }
}

@Composable
private fun ColorButton(
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

private fun monthYear(added: String): String {
    if (added.isBlank()) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        val out = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
        out.format(parser.parse(added)!!)
    } catch (_: Exception) {
        added.take(7)
    }
}
