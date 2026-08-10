package com.tenkultra.tv.presentation.screens.media

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.tenkultra.tv.presentation.common.onTap
import com.tenkultra.tv.presentation.theme.appPalette
import com.tenkultra.tv.presentation.theme.classicRowBrush
import com.tenkultra.tv.presentation.theme.screenBackgroundBrush

private fun storagePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

@Composable
fun MediaBrowserScreen(
    onBack: () -> Unit,
    onPlayFile: (path: String, name: String) -> Unit,
    viewModel: MediaBrowserViewModel = hiltViewModel()
) {
    val palette = appPalette
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    var selectedIndex by remember { mutableIntStateOf(0) }

    var hasPermission by remember {
        mutableStateOf(
            storagePermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.any { it } || hasPermission
        if (hasPermission) viewModel.loadRoots()
    }

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        if (hasPermission) viewModel.loadRoots() else launcher.launch(storagePermissions())
    }
    LaunchedEffect(state.entries.size) {
        if (selectedIndex > state.entries.lastIndex) selectedIndex = 0
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
                    Key.DirectionUp -> { if (selectedIndex > 0) selectedIndex--; true }
                    Key.DirectionDown -> { if (selectedIndex < state.entries.lastIndex) selectedIndex++; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        state.entries.getOrNull(selectedIndex)?.let { entry ->
                            if (entry.isDirectory) { viewModel.open(entry); selectedIndex = 0 }
                            else onPlayFile(entry.path, entry.name)
                        }
                        true
                    }
                    Key.Back -> {
                        if (!viewModel.goUp()) onBack() else selectedIndex = 0
                        true
                    }
                    else -> false
                }
            }
            .padding(24.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Text("Media Browser", color = palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                state.currentPath ?: "Storage devices",
                color = palette.textSecondary, fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))

            when {
                !hasPermission -> CenterText("Storage permission is required to browse USB/SD. " +
                    "Please allow access when prompted.", palette.textSecondary)
                state.error != null && state.entries.isEmpty() -> CenterText(state.error!!, palette.textSecondary)
                else -> {
                    val listState = rememberLazyListState()
                    LaunchedEffect(selectedIndex) {
                        listState.animateScrollToItem((selectedIndex - 6).coerceAtLeast(0))
                    }
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        itemsIndexed(state.entries) { i, entry ->
                            MediaRow(
                                name = entry.name,
                                isDir = entry.isDirectory,
                                isVideo = entry.isMedia && entry.name.substringAfterLast('.', "").lowercase() !in
                                    setOf("mp3", "aac", "flac", "wav", "ogg", "m4a", "opus"),
                                selected = i == selectedIndex,
                                modifier = Modifier.onTap(i) {
                                    selectedIndex = i
                                    if (entry.isDirectory) { viewModel.open(entry); selectedIndex = 0 }
                                    else onPlayFile(entry.path, entry.name)
                                }
                            )
                        }
                    }
                    Text(
                        "Back returns to the parent folder.",
                        color = palette.textSecondary, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaRow(name: String, isDir: Boolean, isVideo: Boolean, selected: Boolean, modifier: Modifier = Modifier) {
    val palette = appPalette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (selected) classicRowBrush(palette, true)
                else androidx.compose.ui.graphics.SolidColor(palette.surface.copy(alpha = 0.4f))
            )
            .then(if (selected) Modifier.border(1.dp, palette.primary, RoundedCornerShape(6.dp)) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isDir) Icons.Filled.Folder else if (isVideo) Icons.Filled.Movie else Icons.Filled.AudioFile,
            contentDescription = null,
            tint = if (selected) palette.background else palette.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            color = if (selected) palette.background else palette.text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CenterText(text: String, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = 15.sp, modifier = Modifier.padding(40.dp))
    }
}
