package com.tenkultra.tv.presentation.screens.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.tenkultra.tv.presentation.common.onTap
import com.tenkultra.tv.presentation.theme.appPalette
import com.tenkultra.tv.presentation.theme.classicRowBrush
import com.tenkultra.tv.presentation.theme.screenBackgroundBrush

@Composable
fun VodDetailScreen(
    onBack: () -> Unit,
    onPlay: (cmd: String, title: String, series: Int) -> Unit,
    viewModel: VodDetailViewModel = hiltViewModel()
) {
    val palette = appPalette
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val args = state.args
    val focus = remember { FocusRequester() }

    var episodeIndex by remember { mutableIntStateOf(0) }

    val seasons = state.seasons
    val seasonIndex = state.selectedSeasonIndex
    val season = seasons.getOrNull(seasonIndex)
    val episodes = state.episodes

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(seasonIndex, episodes.size) {
        if (episodeIndex > episodes.lastIndex) episodeIndex = 0
    }

    BackHandler { onBack() }

    fun playEpisode(i: Int) {
        episodeIndex = i
        episodes.getOrNull(i)?.let { ep ->
            // Resolve the episode's REAL cmd (portal &episode_id= call) before playing, so we
            // never send a guessed file id that resolves to a random episode/movie. The ViewModel
            // falls back to the episode's own cmd + series index if resolution isn't possible.
            val title = "${args.name} · S${season?.number ?: 1}E${ep.number}"
            viewModel.resolveAndPlay(ep, season) { cmd, series ->
                viewModel.prepareVodPlayback(cmd, series, title)
                onPlay(cmd, title, series)
            }
        }
    }
    fun changeSeason(delta: Int) {
        val ni = seasonIndex + delta
        if (ni in seasons.indices) { viewModel.selectSeason(ni); episodeIndex = 0 }
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
                    Key.Back -> { onBack(); true }
                    Key.DirectionLeft -> {
                        if (args.isSeries && seasonIndex > 0) { viewModel.selectSeason(seasonIndex - 1); episodeIndex = 0 }
                        true
                    }
                    Key.DirectionRight -> {
                        if (args.isSeries && seasonIndex < seasons.lastIndex) { viewModel.selectSeason(seasonIndex + 1); episodeIndex = 0 }
                        true
                    }
                    Key.DirectionUp -> { if (episodeIndex > 0) episodeIndex--; true }
                    Key.DirectionDown -> { if (episodeIndex < episodes.lastIndex) episodeIndex++; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (args.isSeries) {
                            playEpisode(episodeIndex)
                        } else {
                            viewModel.prepareVodPlayback(args.cmd, 0, args.name)
                            onPlay(args.cmd, args.name, 0)
                        }
                        true
                    }
                    else -> false
                }
            }
            .padding(28.dp)
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(240.dp)
                    .aspectRatio(0.66f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surface),
                contentAlignment = Alignment.Center
            ) {
                if (args.posterUrl != null) {
                    AsyncImage(model = args.posterUrl, contentDescription = args.name, modifier = Modifier.fillMaxSize())
                } else {
                    Text(args.name.take(2).uppercase(), color = palette.primary, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(28.dp))

            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(args.name, color = palette.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        if (args.year.isNotEmpty()) append(args.year)
                        if (args.genre.isNotEmpty()) { if (isNotEmpty()) append("  •  "); append(args.genre) }
                        append(if (args.isSeries) "  •  Series" else "  •  Movie")
                    },
                    color = palette.primary, fontSize = 15.sp
                )
                if (args.description.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(args.description, color = palette.textSecondary, fontSize = 14.sp, maxLines = 3)
                }
                Spacer(Modifier.height(18.dp))

                when {
                    state.loading -> Text("Loading seasons…", color = palette.text)
                    args.isSeries && seasons.isEmpty() ->
                        Text(state.error ?: "No seasons found.", color = Color(0xFFFF8A80))
                    args.isSeries -> SeasonEpisodes(
                        seasonLabel = "Season ${season?.number ?: 1}",
                        seasonIndex = seasonIndex,
                        seasonCount = seasons.size,
                        episodes = episodes,
                        selectedEpisode = episodeIndex,
                        loadingEpisodes = state.loadingEpisodes,
                        emptyMessage = state.error,
                        onEpisodeTap = ::playEpisode,
                        onSeasonChange = ::changeSeason
                    )
                    else -> PlayButton(onClick = {
                        viewModel.prepareVodPlayback(args.cmd, 0, args.name)
                        onPlay(args.cmd, args.name, 0)
                    })
                }
            }
        }
    }
}

@Composable
private fun SeasonEpisodes(
    seasonLabel: String,
    seasonIndex: Int,
    seasonCount: Int,
    episodes: List<com.tenkultra.tv.domain.model.Episode>,
    selectedEpisode: Int,
    loadingEpisodes: Boolean,
    emptyMessage: String?,
    onEpisodeTap: (Int) -> Unit,
    onSeasonChange: (Int) -> Unit
) {
    val palette = appPalette
    Column(Modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (seasonCount > 1) Text("‹  ", color = palette.primary, fontSize = 22.sp,
                modifier = Modifier.onTap("prev") { onSeasonChange(-1) })
            Text(seasonLabel, color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (seasonCount > 1) Text("  ›", color = palette.primary, fontSize = 22.sp,
                modifier = Modifier.onTap("next") { onSeasonChange(1) })
            if (seasonCount > 1) Text("   (${seasonIndex + 1}/$seasonCount) — ‹ › to change season",
                color = palette.textSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))

        when {
            loadingEpisodes -> Text("Loading episodes…", color = palette.textSecondary, fontSize = 14.sp)
            episodes.isEmpty() -> Text(emptyMessage ?: "No episodes.", color = palette.textSecondary, fontSize = 14.sp)
            else -> {
                val listState = rememberLazyListState()
                LaunchedEffect(selectedEpisode) { listState.animateScrollToItem((selectedEpisode - 4).coerceAtLeast(0)) }
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().fillMaxHeight()
                ) {
                    itemsIndexed(episodes) { index, ep ->
                        val selected = index == selectedEpisode
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(classicRowBrush(palette, selected))
                                .then(if (selected) Modifier.border(1.dp, palette.primary, RoundedCornerShape(8.dp)) else Modifier)
                                .onTap(index) { onEpisodeTap(index) }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                ep.title,
                                color = if (selected) palette.background else palette.text,
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayButton(onClick: () -> Unit) {
    val palette = appPalette
    Box(
        modifier = Modifier
            .width(200.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(classicRowBrush(palette, selected = true))
            .border(1.dp, palette.primary, RoundedCornerShape(10.dp))
            .onTap("play") { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("▶  Play", color = palette.background, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
