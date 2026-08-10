package com.tenkultra.tv.presentation.screens.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.tenkultra.tv.domain.model.Category
import com.tenkultra.tv.presentation.common.LoadingDots
import com.tenkultra.tv.presentation.common.onTap
import com.tenkultra.tv.presentation.common.premiumFocus
import com.tenkultra.tv.presentation.theme.AppPalette
import com.tenkultra.tv.presentation.theme.appPalette
import com.tenkultra.tv.presentation.theme.classicRowBrush
import com.tenkultra.tv.presentation.theme.screenBackgroundBrush
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenVodCategory: (Category) -> Unit = {},
    onOpenTvGenre: (Category) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenMedia: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val palette = appPalette
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }

    // Saveable so returning from a category/channel list restores the tab + row you were on.
    var tabIndex by rememberSaveable { mutableIntStateOf(HomeTab.TV.ordinal) }
    var listIndex by rememberSaveable { mutableIntStateOf(0) }

    val tabs = HomeTab.entries
    val tab = tabs[tabIndex]
    val categories = when (tab) {
        HomeTab.TV -> state.tvGenres
        HomeTab.VIDEO_CLUB -> state.vodCategories
        else -> emptyList()
    }

    // Switching tab resets the category selection to the first item ("All"),
    // so a fresh tab always opens with "All" pre-selected.
    fun selectTab(newIndex: Int) {
        val ni = newIndex.coerceIn(0, tabs.lastIndex)
        if (ni != tabIndex) { tabIndex = ni; listIndex = 0 }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(categories.size) {
        if (listIndex > categories.lastIndex) listIndex = 0
    }
    // Predictive prefetch: warm the highlighted category's first page while the user is still
    // deciding, so pressing OK opens the list instantly (cache hit) instead of waiting on the portal.
    LaunchedEffect(listIndex, tabIndex, categories.size) {
        val cat = categories.getOrNull(listIndex) ?: return@LaunchedEffect
        when (tab) {
            HomeTab.TV -> viewModel.prefetchCategory(cat, isTv = true)
            HomeTab.VIDEO_CLUB -> viewModel.prefetchCategory(cat, isTv = false)
            else -> {}
        }
    }

    // Adult categories still ask for the parental PIN — but through the app-wide CHILD LOCK gate
    // that wraps the NavHost (it guards onOpenTvGenre / onOpenVodCategory), not a local dialog.
    // Gating here as well would ask for the same PIN twice, so this screen just opens.
    fun openSelected() {
        when (tab) {
            HomeTab.TV -> categories.getOrNull(listIndex)?.let(onOpenTvGenre)
            HomeTab.VIDEO_CLUB -> categories.getOrNull(listIndex)?.let(onOpenVodCategory)
            HomeTab.SETTINGS -> onOpenSettings()
            HomeTab.MEDIA_BROWSER -> onOpenMedia()
        }
    }
    fun onCategoryTap(i: Int) { listIndex = i; openSelected() }
    fun onTabTap(i: Int) {
        when (tabs[i]) {
            HomeTab.SETTINGS -> onOpenSettings()
            HomeTab.MEDIA_BROWSER -> onOpenMedia()
            else -> selectTab(i)
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
                when (event.key) {
                    // Left/Right ALWAYS switch the carousel tab (TV ↔ Video Club …),
                    // even while a category is highlighted.
                    Key.DirectionLeft -> { selectTab(tabIndex - 1); true }
                    Key.DirectionRight -> { selectTab(tabIndex + 1); true }
                    // Up/Down move within the always-active category list.
                    Key.DirectionUp -> { if (listIndex > 0) listIndex--; true }
                    Key.DirectionDown -> { if (listIndex < categories.lastIndex) listIndex++; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { openSelected(); true }
                    Key.Back -> false
                    else -> false
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ClockHeader(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 24.dp, bottom = 8.dp)
            )
            // Middle region between the clock and the carousel — the popup centers
            // here so it never overlaps the bottom carousel.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (categories.isNotEmpty()) {
                    CategoryPopup(
                        categories = categories,
                        selectedIndex = listIndex,
                        active = true, // category list is always active → "All" stays pre-selected
                        onSelect = ::onCategoryTap
                    )
                } else if (state.loading) {
                    LoadingDots()
                }
            }
            Carousel(
                tabs = tabs,
                selectedIndex = tabIndex,
                carouselActive = true, // current tab always shows as selected
                onSelect = ::onTabTap,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            )
        }
    }
}

@Composable
private fun ClockHeader(modifier: Modifier = Modifier) {
    val palette = appPalette
    val now by produceState(initialValue = Date()) {
        while (true) { value = Date(); delay(1000) }
    }
    val dateFmt = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH) }
    val timeFmt = remember { SimpleDateFormat("h:mm", Locale.ENGLISH) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(dateFmt.format(now), color = palette.text, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(timeFmt.format(now), color = palette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryPopup(
    categories: List<Category>,
    selectedIndex: Int,
    active: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = appPalette
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem((selectedIndex - 2).coerceAtLeast(0))
    }
    Column(
        modifier = modifier
            .width(520.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface.copy(alpha = 0.96f))
            .border(1.dp, palette.primary.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.KeyboardArrowUp, null, tint = palette.primary, modifier = Modifier.size(26.dp))
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(380.dp).padding(vertical = 6.dp)
        ) {
            itemsIndexed(categories) { index, category ->
                CategoryButton(
                    title = category.title,
                    selected = index == selectedIndex && active,
                    modifier = Modifier.onTap(index) { onSelect(index) }
                )
            }
        }
        Icon(Icons.Filled.KeyboardArrowDown, null, tint = palette.primary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CategoryButton(title: String, selected: Boolean, modifier: Modifier = Modifier) {
    val palette = appPalette
    val shape = RoundedCornerShape(12.dp)
    // Bigger, clearer rows — and the selected one grows further so it reads from across
    // the room (client feedback: the category list looked too small).
    val rowHeight by animateDpAsState(if (selected) 60.dp else 52.dp, tween(180), label = "catH")
    val titleSize by animateFloatAsState(if (selected) 21f else 18f, tween(180), label = "catSz")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .premiumFocus(selected, shape, glow = palette.primary, scaleTo = 1.06f, lift = 14)
            .clip(shape)
            .background(classicRowBrush(palette, selected))
            .then(
                if (selected) Modifier.border(1.dp, palette.primary, shape) else Modifier
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = title,
            color = if (selected) palette.background else palette.text,
            fontSize = titleSize.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Carousel(
    tabs: List<HomeTab>,
    selectedIndex: Int,
    carouselActive: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val prevIdx = selectedIndex - 1
        val nextIdx = selectedIndex + 1
        CarouselSlot(tabs.getOrNull(prevIdx), highlighted = false, arrow = Arrow.LEFT,
            onClick = { if (prevIdx in tabs.indices) onSelect(prevIdx) })
        Spacer(Modifier.width(16.dp))
        CarouselSlot(tabs[selectedIndex], highlighted = carouselActive, arrow = Arrow.NONE,
            onClick = { onSelect(selectedIndex) })
        Spacer(Modifier.width(16.dp))
        CarouselSlot(tabs.getOrNull(nextIdx), highlighted = false, arrow = Arrow.RIGHT,
            onClick = { if (nextIdx in tabs.indices) onSelect(nextIdx) })
    }
}

private enum class Arrow { LEFT, RIGHT, NONE }

@Composable
private fun CarouselSlot(tab: HomeTab?, highlighted: Boolean, arrow: Arrow, onClick: () -> Unit) {
    val palette = appPalette
    val slotWidth = 300.dp
    if (tab == null) {
        Spacer(Modifier.width(slotWidth))
        return
    }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.width(slotWidth).onTap(tab) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .premiumFocus(highlighted, shape, glow = palette.primary, scaleTo = 1.1f, lift = 24)
                .alpha(if (highlighted) 1f else 0.7f)
                .clip(shape)
                .background(tabGradient(palette, tab, highlighted))
                .then(
                    if (highlighted) Modifier.border(2.dp, palette.primary, shape) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tabIcon(tab),
                contentDescription = tab.label,
                tint = if (highlighted) palette.background else palette.text,
                modifier = Modifier.size(54.dp)
            )
            when (arrow) {
                Arrow.LEFT -> Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = palette.text,
                    modifier = Modifier.align(Alignment.CenterStart).size(40.dp)
                )
                Arrow.RIGHT -> Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = palette.text,
                    modifier = Modifier.align(Alignment.CenterEnd).size(40.dp)
                )
                Arrow.NONE -> Unit
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = tab.label,
            color = if (highlighted) palette.text else palette.textSecondary,
            fontSize = 18.sp,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

private fun tabIcon(tab: HomeTab): ImageVector = when (tab) {
    HomeTab.MEDIA_BROWSER -> Icons.Filled.Usb
    HomeTab.TV -> Icons.Filled.LiveTv
    HomeTab.VIDEO_CLUB -> Icons.Filled.Movie
    HomeTab.SETTINGS -> Icons.Filled.Settings
}

private fun tabGradient(palette: AppPalette, tab: HomeTab, highlighted: Boolean): Brush =
    if (highlighted) {
        Brush.linearGradient(listOf(lerp(palette.primary, Color.White, 0.1f), palette.selected))
    } else {
        Brush.linearGradient(
            listOf(
                lerp(palette.surface, palette.selected, 0.4f),
                lerp(palette.surface, palette.background, 0.4f)
            )
        )
    }
