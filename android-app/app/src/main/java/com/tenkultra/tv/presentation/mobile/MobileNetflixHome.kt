package com.tenkultra.tv.presentation.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.tenkultra.tv.domain.model.Channel
import com.tenkultra.tv.domain.model.VodItem
import com.tenkultra.tv.presentation.common.BrandWordmark
import com.tenkultra.tv.presentation.common.LocalParentalGate
import com.tenkultra.tv.presentation.screens.cinematic.CinematicHomeViewModel
import com.tenkultra.tv.presentation.screens.cinematic.CinematicRail
import com.tenkultra.tv.presentation.screens.cinematic.RailKind
import com.tenkultra.tv.presentation.theme.appPalette

private val EDGE = 16.dp
private const val POSTER_RATIO = 0.68f

/**
 * MOBILE "Home" — the Netflix surface for phones: a full-bleed hero for the first title, then
 * horizontal rails for every genre / category, loaded lazily as they scroll into view.
 *
 * It shares [CinematicHomeViewModel] with the TV Cinematic layout, so "Continue watching" and
 * "My list" appear on the phone too, kept in sync with whatever was watched on the box.
 *
 * The category-first Live TV / VOD tabs (which page through EVERY item) stay where they were —
 * this is the browse surface, those are the full catalogue.
 */
@Composable
fun MobileNetflixHome(
    onPlayChannel: (Channel) -> Unit,
    onOpenVod: (VodItem, String) -> Unit,
    onResumeVod: (String, String, Int) -> Unit,
    viewModel: CinematicHomeViewModel = hiltViewModel()
) {
    val palette = appPalette
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val gate = LocalParentalGate.current

    // Phones scroll one continuous page, so show everything together — but VOD first, because a
    // poster wall is what makes the top of the page look like Netflix (live tiles are just logos).
    val rows = remember(state.rails) {
        state.rails.sortedBy { if (it.isLive) 1 else 0 }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Warm rails as they approach the viewport — same lazy strategy as the TV layout.
    //
    // The hero occupies LazyColumn index 0, so a list index maps to `rows[index - 1]`. Warming the
    // raw index skipped the first rails entirely, which is why the top of the page sat on
    // "Loading…". Warm the whole visible window plus a few ahead.
    val visible by remember(rows.size) {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo
            (info.firstOrNull()?.index ?: 0) to (info.lastOrNull()?.index ?: 0)
        }
    }
    LaunchedEffect(visible, rows.size) {
        val (first, last) = visible
        val from = (first - 1).coerceAtLeast(0)
        val to = last - 1 + 3
        // Pass the rail itself: `rows` is re-ordered (VOD first), so an index into it would warm
        // a different rail than the one coming into view.
        for (i in from..to) rows.getOrNull(i)?.let { viewModel.ensureRail(it) }
    }

    // Prefer a poster; fall back to the first live channel so the hero is never an empty black band
    // while the VOD rails are still loading.
    val heroRail = rows.firstOrNull { !it.isLive && it.vod.isNotEmpty() }
    val hero = heroRail?.vod?.firstOrNull()
    val heroChannel = if (hero == null) rows.firstOrNull { it.isLive && it.channels.isNotEmpty() }?.channels?.firstOrNull() else null

    if (state.loading && rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = palette.primary)
        }
        return
    }

    fun openVod(rail: CinematicRail, item: VodItem) {
        gate.guard(rail.title) {
            if (rail.kind == RailKind.CONTINUE) {
                val series = rail.seriesByCmd[item.cmd] ?: 0
                viewModel.prepareVodPlayback(item, series)
                onResumeVod(item.cmd, item.name, series)
            } else {
                viewModel.prepareVodPlayback(item)
                onOpenVod(item, rail.id)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(palette.background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "__hero") {
            MobileHero(
                item = hero,
                channel = heroChannel,
                onPlay = {
                    when {
                        hero != null && heroRail != null -> openVod(heroRail, hero)
                        heroChannel != null -> {
                            val r = rows.first { it.isLive && it.channels.isNotEmpty() }
                            gate.guard(r.title) {
                                viewModel.prepareLivePlayback(r, 0)
                                onPlayChannel(heroChannel)
                            }
                        }
                    }
                }
            )
        }
        itemsIndexed(rows, key = { _, r -> "${r.isLive}:${r.id}" }) { _, rail ->
            MobileRail(
                rail = rail,
                onOpenVod = { item -> openVod(rail, item) },
                onPlayChannel = { ch, i ->
                    gate.guard(rail.title) {
                        viewModel.prepareLivePlayback(rail, i)
                        onPlayChannel(ch)
                    }
                }
            )
        }
    }
}

/** Full-bleed hero for the first title, with the brand mark and a Play button over the artwork. */
@Composable
private fun MobileHero(item: VodItem?, channel: Channel?, onPlay: () -> Unit) {
    val palette = appPalette
    val art = item?.posterUrl ?: channel?.logoUrl
    val title = item?.name ?: channel?.name
    // Nothing loaded yet: a short brand strip beats a screen-tall empty black box.
    val ratio = if (art != null) 0.92f else 3.4f
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)   // tall, portrait-ish — the phone shape Netflix uses
    ) {
        if (art != null) {
            // A poster is portrait; blurring a zoomed copy behind the sharp one fills the frame
            // without stretching the artwork.
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.6f,
                modifier = Modifier.fillMaxSize().blur(30.dp)
            )
            AsyncImage(
                model = art,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(if (item != null) 0.dp else 48.dp)
            )
        } else {
            Box(Modifier.fillMaxSize().background(palette.surface))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to palette.background.copy(alpha = 0.85f),
                    0.24f to Color.Transparent,
                    // Strong enough that the title never fights the artwork's own lettering.
                    0.52f to palette.background.copy(alpha = 0.45f),
                    0.72f to palette.background.copy(alpha = 0.88f),
                    1f to palette.background
                )
            )
        )
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = EDGE, top = 12.dp)
        ) {
            BrandWordmark(modifier = Modifier.width(120.dp), glow = false)
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = EDGE, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (title != null) {
                Text(
                    title,
                    color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    item?.year?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = palette.textSecondary, fontSize = 13.sp)
                        Spacer(Modifier.width(10.dp))
                    }
                    item?.genre?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it, color = palette.textSecondary, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.primary)
                        .clickable { onPlay() }
                        .padding(horizontal = 34.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PlayArrow, null, tint = palette.background,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (item?.isSeries == true) "Watch" else "Play",
                        color = palette.background, fontSize = 15.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** One horizontal rail: heading plus a swipeable row of posters (or channel tiles for live). */
@Composable
private fun MobileRail(
    rail: CinematicRail,
    onOpenVod: (VodItem) -> Unit,
    onPlayChannel: (Channel, Int) -> Unit
) {
    val palette = appPalette
    val locked = LocalParentalGate.current.isRestricted(rail.title)
    if (rail.loaded && rail.size == 0) return

    Column(Modifier.padding(top = 18.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = EDGE, bottom = 8.dp)
        ) {
            Text(rail.title, color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (locked) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.Lock, null, tint = palette.primary, modifier = Modifier.size(14.dp))
            }
        }
        if (!rail.loaded && rail.size == 0) {
            Row(Modifier.padding(start = EDGE), verticalAlignment = Alignment.CenterVertically) {
                Text("Loading…", color = palette.textSecondary, fontSize = 12.sp)
            }
            return@Column
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = EDGE)
        ) {
            if (rail.isLive) {
                itemsIndexed(rail.channels, key = { _, ch -> ch.id }) { i, ch ->
                    MobileChannelTile(ch) { onPlayChannel(ch, i) }
                }
            } else {
                items(rail.vod, key = { it.id }) { item ->
                    MobilePosterTile(
                        item = item,
                        progress = rail.progressByCmd[item.cmd] ?: 0f,
                        onClick = { onOpenVod(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MobilePosterTile(item: VodItem, progress: Float, onClick: () -> Unit) {
    val palette = appPalette
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .width(112.dp)
            .aspectRatio(POSTER_RATIO)
            .clip(shape)
            .background(palette.surface)
            .clickable { onClick() }
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
                Text(
                    item.name.take(2).uppercase(),
                    color = palette.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        if (progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp, vertical = 5.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.primary)
                )
            }
        }
    }
}

@Composable
private fun MobileChannelTile(channel: Channel, onClick: () -> Unit) {
    val palette = appPalette
    val shape = RoundedCornerShape(8.dp)
    Column(Modifier.width(150.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(palette.surface)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (channel.logoUrl != null) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(14.dp)
                )
            } else {
                Text(
                    channel.name.take(2).uppercase(),
                    color = palette.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
            }
            Row(
                Modifier.align(Alignment.TopStart).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(palette.liveDot))
                Spacer(Modifier.width(4.dp))
                Text("LIVE", color = palette.text, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            if (channel.number.isNotBlank()) "${channel.number}  ${channel.name}" else channel.name,
            color = palette.textSecondary, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
