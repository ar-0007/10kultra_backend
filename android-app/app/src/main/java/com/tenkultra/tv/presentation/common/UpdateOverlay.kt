package com.tenkultra.tv.presentation.common

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.tenkultra.tv.data.update.UpdateInfo
import com.tenkultra.tv.data.update.UpdateManager
import com.tenkultra.tv.data.update.UpdateState
import com.tenkultra.tv.presentation.theme.appPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val manager: UpdateManager
) : ViewModel() {
    val state = manager.state
    private var started = false

    /** Silent startup check — runs once, never blocks or shows errors. */
    fun checkOnStart() {
        if (started) return
        started = true
        viewModelScope.launch { manager.check(silent = true) }
    }

    /** Manual "Check for updates" from Settings — reports up-to-date / errors too. */
    fun checkNow() = viewModelScope.launch { manager.check(silent = false) }

    fun update(info: UpdateInfo) = viewModelScope.launch { manager.downloadAndInstall(info) }

    fun dismiss() = manager.dismiss()
}

/**
 * App-wide OTA overlay. Mounted once over the whole app: it fires a silent update check on
 * launch and, when the dashboard offers a newer build, shows an "Update Available" dialog,
 * then the download progress and install. A forced update can't be dismissed.
 */
@Composable
fun UpdateOverlay(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.checkOnStart() }

    when (val s = state) {
        is UpdateState.Available -> UpdateDialog(
            info = s.info,
            onUpdate = { viewModel.update(s.info) },
            onLater = if (s.info.force) null else viewModel::dismiss
        )
        is UpdateState.Downloading -> UpdateProgress("Downloading update…  ${s.percent}%")
        UpdateState.Installing -> UpdateProgress("Installing…")
        else -> Unit // Idle / Checking / UpToDate / Error are silent for the app-wide overlay
    }
}

@Composable
private fun UpdateDialog(info: UpdateInfo, onUpdate: () -> Unit, onLater: (() -> Unit)?) {
    val palette = appPalette
    val fr = remember { FocusRequester() }
    // 0 = Update now, 1 = Later. Forced updates have no Later, so selection stays on Update.
    var sel by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }

    // A real Dialog window — it OWNS input focus, so the screen behind never reacts to the
    // remote while the prompt is up (an in-tree overlay loses the focus fight with the screen).
    Dialog(
        onDismissRequest = { onLater?.invoke() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = onLater != null,
            dismissOnClickOutside = false
        )
    ) {
      Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .focusRequester(fr)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                when (e.key) {
                    Key.DirectionLeft -> { sel = 0; true }
                    Key.DirectionRight -> { if (onLater != null) sel = 1; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (sel == 0) onUpdate() else onLater?.invoke(); true
                    }
                    Key.Back -> { onLater?.invoke(); true }
                    else -> true // swallow everything else
                }
            },
        contentAlignment = Alignment.Center
      ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(palette.surface)
                .padding(32.dp)
        ) {
            Text(
                if (info.force) "Update Required" else "Update Available",
                color = palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "A new version (${info.versionName}) is available." +
                    if (info.sizeBytes > 0) "  ·  ${formatSize(info.sizeBytes)}" else "",
                color = palette.textSecondary, fontSize = 15.sp
            )
            if (info.changelog.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("What's new", color = palette.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(info.changelog, color = palette.text, fontSize = 14.sp, lineHeight = 20.sp)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton("Update now", selected = sel == 0, primary = true, modifier = Modifier.weight(1f))
                if (onLater != null) {
                    DialogButton("Later", selected = sel == 1, primary = false, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Use ◀ ▶ to choose · OK to confirm",
                color = palette.textSecondary, fontSize = 12.sp
            )
        }
      }
    }
}

@Composable
private fun DialogButton(label: String, selected: Boolean, primary: Boolean, modifier: Modifier = Modifier) {
    val palette = appPalette
    val bg = when {
        selected && primary -> palette.primary
        selected -> palette.text.copy(alpha = 0.18f)
        else -> palette.background.copy(alpha = 0.4f)
    }
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(if (selected) Modifier.border(2.dp, palette.primary, RoundedCornerShape(8.dp)) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected && primary) palette.background else palette.text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun UpdateProgress(label: String) {
    val palette = appPalette
    BackHandler { } // can't cancel mid-update
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surface)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoadingDots()
            Spacer(Modifier.height(16.dp))
            Text(label, color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text("Please keep the box on.", color = palette.textSecondary, fontSize = 13.sp)
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) "%.1f MB".format(mb) else "%d KB".format(bytes / 1024)
}
