package com.tenkultra.tv.presentation.mobile

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tenkultra.tv.presentation.screens.vod.VodDetailViewModel
import com.tenkultra.tv.presentation.theme.appPalette

/** MOBILE series detail — poster + info, season chips, and a tap-to-play episodes list. */
@Composable
fun MobileVodDetailScreen(
    onBack: () -> Unit,
    onPlay: (cmd: String, title: String, series: Int) -> Unit,
    viewModel: VodDetailViewModel = hiltViewModel()
) {
    val palette = appPalette
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler { onBack() }

    val colors = darkColorScheme(
        primary = palette.primary, onPrimary = palette.background,
        background = palette.background, onBackground = palette.text,
        surface = palette.surface, onSurface = palette.text
    )

    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize().background(palette.background)) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        Box(
                            Modifier.width(120.dp).aspectRatio(0.68f).clip(RoundedCornerShape(10.dp))
                                .background(palette.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.args.posterUrl != null) {
                                AsyncImage(
                                    model = state.args.posterUrl, contentDescription = state.args.name,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(state.args.name.take(2).uppercase(), color = palette.primary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(state.args.name, color = palette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            val meta = listOf(state.args.year, state.args.genre).filter { it.isNotBlank() }.joinToString(" · ")
                            if (meta.isNotBlank()) Text(meta, color = palette.textSecondary, fontSize = 13.sp)
                            if (state.args.description.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(state.args.description, color = palette.text.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 5, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                            }
                        }
                    }
                }

                // Movie (not a series) → single Play button.
                if (!state.args.isSeries) {
                    item {
                        Card(
                            onClick = { onPlay(state.args.cmd, state.args.name, 0) },
                            colors = CardDefaults.cardColors(containerColor = palette.primary),
                            modifier = Modifier.padding(16.dp).fillMaxWidth()
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Filled.PlayArrow, null, tint = palette.background)
                                Spacer(Modifier.width(8.dp))
                                Text("Play", color = palette.background, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    return@LazyColumn
                }

                // Season chips
                if (state.seasons.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            itemsIndexedChips(state.seasons.size, state.selectedSeasonIndex, palette) { i ->
                                viewModel.selectSeason(i)
                            }
                        }
                    }
                }

                if (state.loadingEpisodes && state.episodes.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = palette.primary)
                        }
                    }
                }

                items(state.episodes, key = { "${it.id}-${it.number}" }) { ep ->
                    val season = state.seasons.getOrNull(state.selectedSeasonIndex)
                    Card(
                        onClick = { viewModel.resolveAndPlay(ep, season) { cmd, series -> onPlay(cmd, ep.title.ifBlank { state.args.name }, series) } },
                        colors = CardDefaults.cardColors(containerColor = palette.surface),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.width(38.dp).height(38.dp).clip(RoundedCornerShape(19.dp)).background(palette.primary),
                                contentAlignment = Alignment.Center
                            ) { Text("${ep.number}", color = palette.background, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(12.dp))
                            Text(ep.title.ifBlank { "Episode ${ep.number}" }, color = palette.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.PlayArrow, null, tint = palette.primary)
                        }
                    }
                }
            }

            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = palette.text)
            }
        }
    }
}

/** Season chips row helper (kept simple to avoid extra models). */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedChips(
    count: Int, selected: Int, palette: com.tenkultra.tv.presentation.theme.AppPalette, onSelect: (Int) -> Unit
) {
    items(count) { i ->
        FilterChip(
            selected = i == selected,
            onClick = { onSelect(i) },
            label = { Text("Season ${i + 1}") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = palette.primary,
                selectedLabelColor = palette.background,
                containerColor = palette.surface,
                labelColor = palette.textSecondary
            )
        )
    }
}
