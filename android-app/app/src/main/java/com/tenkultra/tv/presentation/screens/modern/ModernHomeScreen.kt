package com.tenkultra.tv.presentation.screens.modern

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.scale
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tenkultra.tv.domain.model.Category
import com.tenkultra.tv.domain.model.Channel
import com.tenkultra.tv.domain.model.VodItem
import com.tenkultra.tv.presentation.common.BrandWordmark
import com.tenkultra.tv.presentation.common.LocalParentalGate
import com.tenkultra.tv.presentation.common.onTap
import com.tenkultra.tv.presentation.common.premiumFocus
import com.tenkultra.tv.presentation.theme.appPalette
import com.tenkultra.tv.presentation.theme.classicRowBrush
import com.tenkultra.tv.presentation.theme.screenBackgroundBrush

private enum class Zone { RAIL, CATEGORY, CONTENT }
private const val GRID_COLS = 5

@Composable
fun ModernHomeScreen(
    onOpenSettings: () -> Unit,
    onOpenVodItem: (VodItem, String) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    viewModel: ModernHomeViewModel = hiltViewModel()
) {
    val palette = appPalette
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    // CHILD LOCK — the gate works at CATEGORY level: an adult genre/category needs the PIN, and
    // whatever is opened from inside it is gated by that same category title.
    val gate = LocalParentalGate.current

    // Saveable so returning from the player/detail restores where the cursor was (not the top).
    var zone by rememberSaveable { mutableStateOf(Zone.CATEGORY) }
    var railIndex by rememberSaveable { mutableIntStateOf(1) } // 0=Live TV, 1=VOD, 2=Settings
    var categoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var contentIndex by rememberSaveable { mutableIntStateOf(0) }

    val categories = state.categories()
    val isVod = state.section == ModernSection.VOD
    val contentSize = if (isVod) state.vodItems.size else state.channels.size
    val cols = if (isVod) GRID_COLS else 1

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(state.section, categories.size) {
        if (categoryIndex > categories.lastIndex) categoryIndex = 0
    }
    LaunchedEffect(contentSize) {
        if (contentIndex > contentSize - 1) contentIndex = (contentSize - 1).coerceAtLeast(0)
    }

    fun moveCategory(delta: Int) {
        if (categories.isEmpty()) return
        val ni = (categoryIndex + delta).coerceIn(0, categories.lastIndex)
        if (ni != categoryIndex) {
            categoryIndex = ni
            contentIndex = 0
            // CHILD LOCK — a locked category never auto-loads its items on hover; OK asks for the PIN.
            if (gate.isRestricted(categories[ni].title)) viewModel.clearContent()
            else viewModel.selectCategory(categories[ni])
        }
    }

    // ---- Touch / tap handlers (phones) ----
    fun onRailTap(i: Int) {
        railIndex = i
        when (i) {
            0 -> { viewModel.setSection(ModernSection.LIVE_TV); categoryIndex = 0; contentIndex = 0; zone = Zone.CATEGORY }
            1 -> { viewModel.setSection(ModernSection.VOD); categoryIndex = 0; contentIndex = 0; zone = Zone.CATEGORY }
            2 -> onOpenSettings()
        }
    }
    fun onCategoryTap(i: Int) {
        if (i !in categories.indices) return
        categoryIndex = i
        contentIndex = 0
        // CHILD LOCK — an adult genre/category only opens after the PIN.
        gate.guard(categories[i].title) {
            viewModel.selectCategory(categories[i])
            zone = Zone.CONTENT
        }
    }
    fun onContentTap(i: Int) {
        contentIndex = i
        val cat = categories.getOrNull(categoryIndex)
        val catId = cat?.id ?: "*"
        // CHILD LOCK — items opened from inside an adult category are gated by the category title.
        if (isVod) state.vodItems.getOrNull(i)?.let { item ->
            gate.guard(cat?.title) { viewModel.prepareVodPlayback(item); onOpenVodItem(item, catId) }
        } else state.channels.getOrNull(i)?.let { ch ->
            gate.guard(cat?.title) { viewModel.prepareLivePlayback(state.channels, i); onPlayChannel(ch) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBackgroundBrush(palette))
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (zone) {
                    Zone.RAIL -> when (event.key) {
                        Key.DirectionUp -> { if (railIndex > 0) railIndex--; true }
                        Key.DirectionDown -> { if (railIndex < 2) railIndex++; true }
                        Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            when (railIndex) {
                                0 -> { viewModel.setSection(ModernSection.LIVE_TV); categoryIndex = 0; contentIndex = 0; zone = Zone.CATEGORY }
                                1 -> { viewModel.setSection(ModernSection.VOD); categoryIndex = 0; contentIndex = 0; zone = Zone.CATEGORY }
                                2 -> onOpenSettings()
                            }
                            true
                        }
                        else -> false
                    }
                    Zone.CATEGORY -> when (event.key) {
                        Key.DirectionUp -> { moveCategory(-1); true }
                        Key.DirectionDown -> { moveCategory(1); true }
                        Key.DirectionLeft, Key.Back -> { zone = Zone.RAIL; true }
                        Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            val cat = categories.getOrNull(categoryIndex)
                            // CHILD LOCK — a locked category loads its items only after the PIN.
                            if (cat != null && gate.isRestricted(cat.title)) {
                                gate.guard(cat.title) { viewModel.selectCategory(cat); contentIndex = 0 }
                            } else if (contentSize > 0) { zone = Zone.CONTENT; contentIndex = 0 }
                            true
                        }
                        else -> false
                    }
                    Zone.CONTENT -> when (event.key) {
                        Key.DirectionLeft -> {
                            if (contentIndex % cols == 0) zone = Zone.CATEGORY else contentIndex--
                            true
                        }
                        Key.DirectionRight -> {
                            if (contentIndex % cols != cols - 1 && contentIndex < contentSize - 1) contentIndex++
                            true
                        }
                        Key.DirectionUp -> { if (contentIndex >= cols) contentIndex -= cols; true }
                        Key.DirectionDown -> {
                            if (contentIndex + cols <= contentSize - 1) contentIndex += cols
                            if (contentIndex >= contentSize - cols * 2) viewModel.loadMore()
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            val i = contentIndex
                            val cat = categories.getOrNull(categoryIndex)
                            val catId = cat?.id ?: "*"
                            // CHILD LOCK — OK inside an adult category asks for the PIN first.
                            if (isVod) state.vodItems.getOrNull(i)?.let { item ->
                                gate.guard(cat?.title) {
                                    viewModel.prepareVodPlayback(item); onOpenVodItem(item, catId)
                                }
                            } else state.channels.getOrNull(i)?.let { ch ->
                                gate.guard(cat?.title) {
                                    viewModel.prepareLivePlayback(state.channels, i); onPlayChannel(ch)
                                }
                            }
                            true
                        }
                        Key.Back -> { zone = Zone.CATEGORY; true }
                        else -> false
                    }
                }
            }
    ) {
        Row(Modifier.fillMaxSize()) {
            NavRail(railIndex = railIndex, railActive = zone == Zone.RAIL, onSelect = ::onRailTap)
            CategoryColumn(
                title = if (isVod) "VOD" else "LIVE TV",
                categories = categories,
                selectedIndex = categoryIndex,
                active = zone == Zone.CATEGORY,
                onSelect = ::onCategoryTap,
                modifier = Modifier.width(240.dp).fillMaxHeight()
            )
            Box(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                when {
                    state.loadingContent && contentSize == 0 ->
                        com.tenkultra.tv.presentation.common.LoadingDots(modifier = Modifier.align(Alignment.Center))
                    isVod -> VodGrid(items = state.vodItems, selectedIndex = contentIndex, active = zone == Zone.CONTENT, onClick = ::onContentTap)
                    else -> ChannelColumn(channels = state.channels, selectedIndex = contentIndex, active = zone == Zone.CONTENT, onClick = ::onContentTap)
                }
            }
        }
    }
}

private data class RailEntry(val label: String, val icon: ImageVector)

@Composable
private fun NavRail(railIndex: Int, railActive: Boolean, onSelect: (Int) -> Unit) {
    val palette = appPalette
    val entries = listOf(
        RailEntry("Live TV", Icons.Filled.LiveTv),
        RailEntry("VOD", Icons.Filled.Movie),
        RailEntry("Settings", Icons.Filled.Settings)
    )
    Column(
        modifier = Modifier
            .width(210.dp)
            .fillMaxHeight()
            .background(palette.surface.copy(alpha = 0.55f))
            .padding(16.dp)
    ) {
        BrandWordmark(modifier = Modifier.width(150.dp), glow = false)
        Spacer(Modifier.height(28.dp))
        entries.forEachIndexed { i, entry ->
            val selected = i == railIndex && railActive
            val current = i == railIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) classicRowBrush(palette, true) else androidx.compose.ui.graphics.SolidColor(Color.Transparent))
                    .onTap(i) { onSelect(i) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    entry.icon, null,
                    tint = if (selected) palette.background else if (current) palette.primary else palette.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    entry.label,
                    color = if (selected) palette.background else if (current) palette.text else palette.textSecondary,
                    fontSize = 16.sp,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun CategoryColumn(
    title: String,
    categories: List<Category>,
    selectedIndex: Int,
    active: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = appPalette
    val gate = LocalParentalGate.current
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) { listState.animateScrollToItem((selectedIndex - 4).coerceAtLeast(0)) }
    Column(modifier = modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp)) {
        Text(title, color = palette.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), state = listState) {
            itemsIndexed(categories) { i, cat ->
                val selected = i == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (selected && active) classicRowBrush(palette, true)
                            else if (selected) classicRowBrush(palette, false)
                            else androidx.compose.ui.graphics.SolidColor(palette.surface.copy(alpha = 0.4f))
                        )
                        .onTap(i) { onSelect(i) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        cat.title,
                        color = if (selected && active) palette.background else palette.text,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    // CHILD LOCK — adult genres/categories are marked with a lock.
                    if (gate.isRestricted(cat.title)) {
                        Icon(
                            Icons.Filled.Lock, null,
                            tint = if (selected && active) palette.background else palette.primary,
                            modifier = Modifier.align(Alignment.CenterEnd).size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VodGrid(items: List<VodItem>, selectedIndex: Int, active: Boolean, onClick: (Int) -> Unit) {
    val palette = appPalette
    val gridState = rememberLazyGridState()
    LaunchedEffect(selectedIndex, active) {
        if (active) gridState.animateScrollToItem(selectedIndex.coerceAtLeast(0))
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLS),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        gridItemsIndexed(items, key = { _, item -> item.id }) { i, item ->
            val selected = i == selectedIndex && active
            val shape = RoundedCornerShape(10.dp)
            Column(modifier = Modifier.onTap(i) { onClick(i) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.68f)
                        .premiumFocus(selected, shape, glow = palette.primary, scaleTo = 1.12f, lift = 22)
                        .clip(shape)
                        .background(palette.surface)
                        .then(if (selected) Modifier.border(2.5.dp, palette.primary, shape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.posterUrl != null) {
                        AsyncImage(model = item.posterUrl, contentDescription = item.name, modifier = Modifier.fillMaxSize())
                    } else {
                        Text(item.name.take(2).uppercase(), color = palette.primary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    item.name,
                    color = if (selected) palette.primary else palette.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChannelColumn(channels: List<Channel>, selectedIndex: Int, active: Boolean, onClick: (Int) -> Unit) {
    val palette = appPalette
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, active) {
        if (active) listState.animateScrollToItem((selectedIndex - 5).coerceAtLeast(0))
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(channels, key = { _, ch -> ch.id }) { i, ch ->
            val selected = i == selectedIndex && active
            // Selected channel grows bigger (height + name) for clear across-room reading.
            val rowHeight by animateDpAsState(if (selected) 56.dp else 40.dp, tween(180), label = "mChH")
            val nameSize by animateFloatAsState(if (selected) 18f else 14f, tween(180), label = "mChSz")
            val numSize by animateFloatAsState(if (selected) 15f else 13f, tween(180), label = "mChNum")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (selected) classicRowBrush(palette, true)
                        else androidx.compose.ui.graphics.SolidColor(palette.surface.copy(alpha = 0.4f))
                    )
                    .onTap(i) { onClick(i) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(ch.number.ifBlank { "—" }, color = if (selected) palette.selected else palette.primary,
                    fontSize = numSize.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                Text(ch.name, color = if (selected) palette.background else palette.text, fontSize = nameSize.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (ch.nowPlaying.isNotBlank()) {
                    Text(ch.nowPlaying, color = if (selected) palette.selected else palette.textSecondary,
                        fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
