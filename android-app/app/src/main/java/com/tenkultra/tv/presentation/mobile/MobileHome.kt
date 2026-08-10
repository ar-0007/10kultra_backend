package com.tenkultra.tv.presentation.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tenkultra.tv.domain.model.Category
import com.tenkultra.tv.domain.model.Channel
import com.tenkultra.tv.domain.model.VodItem
import com.tenkultra.tv.presentation.common.LocalParentalGate
import com.tenkultra.tv.presentation.theme.appPalette

/**
 * MOBILE home — a real touch app: bottom navigation (Live TV / Movies / Series / Settings), category
 * chips, and tap-to-open grids. Reuses [MobileHomeViewModel] over the shared data layer. Wrapped in a
 * Material3 dark theme derived from the app palette so it looks native on phones.
 *
 * Live TV flow:
 *   1. Tap "Live TV" → see CATEGORY list (genre cards)
 *   2. Tap a category → see its CHANNELS (grid)
 *   3. Back → returns to category list
 *
 * VOD flow:
 *   Classic chip-based categories on top + poster grid.
 *
 * CHILD LOCK: gating is CATEGORY-level — an adult genre/category asks for the parental PIN before
 * it opens (and wears a lock badge). Once inside, its channels/titles open normally.
 */
@Composable
fun MobileHome(
    onPlayChannel: (Channel) -> Unit,
    onOpenVod: (VodItem, String) -> Unit,
    /** Resume straight into the player from "Continue watching" (cmd, title, episode). */
    onResumeVod: (String, String, Int) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MobileHomeViewModel = hiltViewModel()
) {
    val palette = appPalette
    val state by viewModel.ui.collectAsStateWithLifecycle()
    // App-wide CHILD LOCK — adult categories need the PIN before they open.
    val gate = LocalParentalGate.current

    // Tab index lives in the ViewModel so it survives navigation (Settings → back keeps the tab).
    val tab = state.tabIndex

    val colors = darkColorScheme(
        primary = palette.primary,
        onPrimary = palette.background,
        background = palette.background,
        onBackground = palette.text,
        surface = palette.surface,
        onSurface = palette.text,
        surfaceVariant = palette.surface,
        secondary = palette.selected
    )

    // Handle back press: if drilled into a category (Live TV or VOD), go back to category list
    // instead of leaving the screen.
    BackHandler(enabled = state.drillDown) {
        viewModel.backToCategories()
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = palette.background,
            bottomBar = {
                NavigationBar(containerColor = palette.surface) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = palette.background,
                        selectedTextColor = palette.primary,
                        indicatorColor = palette.primary,
                        unselectedIconColor = palette.textSecondary,
                        unselectedTextColor = palette.textSecondary
                    )
                    NavigationBarItem(
                        selected = tab == 0, colors = itemColors,
                        onClick = { viewModel.selectTab(0) },
                        icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = tab == 1, colors = itemColors,
                        onClick = { viewModel.selectTab(1) },
                        icon = { Icon(Icons.Filled.LiveTv, null) }, label = { Text("Live TV") }
                    )
                    NavigationBarItem(
                        selected = tab == 2, colors = itemColors,
                        onClick = { viewModel.selectTab(2) },
                        icon = { Icon(Icons.Filled.Movie, null) }, label = { Text("VOD") }
                    )
                    NavigationBarItem(
                        selected = tab == 3, colors = itemColors,
                        onClick = { onOpenSettings() },
                        icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") }
                    )
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad).background(palette.background)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn() + slideInHorizontally { it / 6 }) togetherWith
                                (fadeOut() + slideOutHorizontally { -it / 6 })
                    },
                    label = "tab_transition"
                ) { currentTab ->
                    when (currentTab) {
                        0 -> MobileNetflixHome(
                            onPlayChannel = onPlayChannel,
                            onOpenVod = onOpenVod,
                            onResumeVod = onResumeVod
                        )
                        1 -> {
                            // LIVE TV: category list → drill into channels
                            if (state.drillDown) {
                                LiveChannelGrid(
                                    categoryTitle = state.selectedCategoryTitle ?: "Live TV",
                                    state = state,
                                    onBack = { viewModel.backToCategories() },
                                    onLoadMore = viewModel::loadMore,
                                    onPlayChannel = { ch, i ->
                                        viewModel.prepareLivePlayback(i); onPlayChannel(ch)
                                    }
                                )
                            } else {
                                LiveCategoryList(
                                    categories = state.categories,
                                    loading = state.loadingCategories,
                                    onSelectCategory = { cat ->
                                        gate.guard(cat.title) { viewModel.selectCategory(cat.id, cat.title) }
                                    }
                                )
                            }
                        }
                        else -> {
                            // VOD: same category-first flow as Live TV
                            if (state.drillDown) {
                                VodContentGrid(
                                    categoryTitle = state.selectedCategoryTitle ?: "VOD",
                                    state = state,
                                    onBack = { viewModel.backToCategories() },
                                    onLoadMore = viewModel::loadMore,
                                    onOpenVod = onOpenVod
                                )
                            } else {
                                VodCategoryList(
                                    categories = state.categories,
                                    loading = state.loadingCategories,
                                    onSelectCategory = { cat ->
                                        gate.guard(cat.title) { viewModel.selectCategory(cat.id, cat.title) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Live TV — Category List (step 1)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveCategoryList(
    categories: List<Category>,
    loading: Boolean,
    onSelectCategory: (Category) -> Unit
) {
    val palette = appPalette
    Column(Modifier.fillMaxSize()) {
        Text(
            "Live TV", color = palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
        )
        Text(
            "Select a category", color = palette.textSecondary, fontSize = 14.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
        )

        if (loading || categories.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(color = palette.primary)
                else Text("No categories found", color = palette.textSecondary, fontSize = 14.sp)
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(categories, key = { _, cat -> cat.id }) { _, category ->
                CategoryCard(category = category, onClick = { onSelectCategory(category) })
            }
        }
    }
}

/** A tappable category row card — shows the genre name with a chevron (and a lock if adult). */
@Composable
private fun CategoryCard(category: Category, onClick: () -> Unit) {
    val palette = appPalette
    // CHILD LOCK badge — this category asks for the parental PIN when it's opened.
    val locked = LocalParentalGate.current.isRestricted(category.title)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent bar
            Box(
                Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(palette.primary, palette.selected)
                        )
                    )
            )
            Spacer(Modifier.width(14.dp))
            Text(
                category.title,
                color = palette.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (locked) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Locked",
                    tint = palette.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Open",
                tint = palette.textSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Live TV — Channel Grid (step 2, after tapping a category)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveChannelGrid(
    categoryTitle: String,
    state: MobileHomeUiState,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onPlayChannel: (Channel, Int) -> Unit
) {
    val palette = appPalette
    Column(Modifier.fillMaxSize()) {
        // Header with back + category title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, end = 16.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = palette.text
                )
            }
            Text(
                categoryTitle, color = palette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (state.channels.isNotEmpty()) {
                Text(
                    "${state.channels.size} channels",
                    color = palette.textSecondary, fontSize = 13.sp
                )
            }
        }

        if (state.loadingContent && state.channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.primary)
            }
            return
        }

        val gridState = rememberLazyGridState()
        // Keyed on the CURRENT size: without the key the remembered lambda kept comparing against
        // the list as it was on first composition, so paging stopped dead after the first page.
        val count = state.channels.size
        val nearEnd by remember(count) {
            derivedStateOf {
                val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                count > 0 && last >= count - 6
            }
        }
        LaunchedEffect(nearEnd, count) { if (nearEnd) onLoadMore() }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.channels, key = { it.id }) { ch ->
                ChannelCardMobile(ch) { onPlayChannel(ch, state.channels.indexOf(ch)) }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// VOD — Category List (step 1)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun VodCategoryList(
    categories: List<Category>,
    loading: Boolean,
    onSelectCategory: (Category) -> Unit
) {
    val palette = appPalette
    Column(Modifier.fillMaxSize()) {
        Text(
            "Movies & Series", color = palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
        )
        Text(
            "Select a category", color = palette.textSecondary, fontSize = 14.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
        )

        if (loading || categories.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(color = palette.primary)
                else Text("No categories found", color = palette.textSecondary, fontSize = 14.sp)
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(categories, key = { _, cat -> cat.id }) { _, category ->
                CategoryCard(category = category, onClick = { onSelectCategory(category) })
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// VOD — Content Grid (step 2, after tapping a category)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun VodContentGrid(
    categoryTitle: String,
    state: MobileHomeUiState,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenVod: (VodItem, String) -> Unit
) {
    val palette = appPalette
    val vodItems = state.vod

    Column(Modifier.fillMaxSize()) {
        // Header with back + category title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, end = 16.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = palette.text
                )
            }
            Text(
                categoryTitle, color = palette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (vodItems.isNotEmpty()) {
                Text(
                    "${vodItems.size} items",
                    color = palette.textSecondary, fontSize = 13.sp
                )
            }
        }

        if (state.loadingContent && vodItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.primary)
            }
            return
        }

        val gridState = rememberLazyGridState()
        // Same stale-capture fix as the channel grid above.
        val count = vodItems.size
        val nearEnd by remember(count) {
            derivedStateOf {
                val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                count > 0 && last >= count - 6
            }
        }
        LaunchedEffect(nearEnd, count) { if (nearEnd) onLoadMore() }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(vodItems, key = { it.id }) { item ->
                PosterCardMobile(item) { onOpenVod(item, state.selectedCategoryId.orEmpty()) }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Reusable cards
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChannelCardMobile(channel: Channel, onClick: () -> Unit) {
    val palette = appPalette
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                    .background(palette.background),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl != null) {
                    AsyncImage(
                        model = channel.logoUrl, contentDescription = channel.name,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(10.dp)
                    )
                } else {
                    Text(channel.name.take(2).uppercase(), color = palette.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (channel.number.isNotBlank()) "${channel.number}  ${channel.name}" else channel.name,
                color = palette.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PosterCardMobile(item: VodItem, onClick: () -> Unit) {
    val palette = appPalette
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().aspectRatio(0.68f).background(palette.background),
                contentAlignment = Alignment.Center
            ) {
                if (item.posterUrl != null) {
                    AsyncImage(
                        model = item.posterUrl, contentDescription = item.name,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(item.name.take(2).uppercase(), color = palette.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                item.name, color = palette.text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp, modifier = Modifier.padding(8.dp)
            )
        }
    }
}
