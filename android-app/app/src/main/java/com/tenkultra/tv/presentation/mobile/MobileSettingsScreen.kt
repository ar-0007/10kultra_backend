package com.tenkultra.tv.presentation.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenkultra.tv.BuildConfig
import com.tenkultra.tv.data.update.UpdateState
import com.tenkultra.tv.presentation.common.UpdateViewModel
import com.tenkultra.tv.presentation.screens.settings.SettingsViewModel
import com.tenkultra.tv.presentation.theme.appPalette

/**
 * MOBILE settings — touch list with ONLY the mobile-relevant items. TV-only stuff (manual portal URL
 * change, layout switch, reboot, remote, Connect-Android QR) is intentionally NOT here: a phone gets
 * its portal via the QR scan, not by typing.
 */
@Composable
fun MobileSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val palette = appPalette
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateVm: UpdateViewModel = hiltViewModel()
    val updateState by updateVm.state.collectAsStateWithLifecycle()
    BackHandler { onBack() }

    // The row used to say nothing but the build number, so tapping "Software update" looked dead
    // even while a check was running. Mirror the real state back to the user.
    val updateStatus = when (val u = updateState) {
        UpdateState.Checking -> "Checking…"
        UpdateState.UpToDate -> "Up to date · v${BuildConfig.VERSION_NAME}"
        is UpdateState.Available -> "Update available: v${u.info.versionName} — tap to install"
        is UpdateState.Downloading -> "Downloading… ${u.percent}%"
        UpdateState.Installing -> "Installing…"
        is UpdateState.Error -> u.message
        else -> "v${BuildConfig.VERSION_NAME} · tap to check"
    }

    val colors = darkColorScheme(
        primary = palette.primary, onPrimary = palette.background,
        background = palette.background, onBackground = palette.text,
        surface = palette.surface, onSurface = palette.text
    )

    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize().background(palette.background)) {
            LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = palette.text) }
                        Text("Settings", color = palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Subscription / account card
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = palette.surface), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Subscription", color = palette.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            InfoRow("Registered to", state.accountName, palette)
                            InfoRow("Package", state.accountPackage, palette)
                            InfoRow("Status", state.accountStatus, palette, valueColor = if (state.accountStatus == "Active") Color(0xFF5FBF8F) else Color(0xFFD9776A))
                            InfoRow("Expires", state.accountExpiry, palette)
                            state.accountDaysLeft?.let { InfoRow("Time left", if (it >= 0) "$it days" else "Expired", palette) }
                        }
                    }
                }

                item { MobileSettingRow(Icons.Filled.Fingerprint, "MAC address", state.mac, palette) {} }
                item { MobileSettingRow(Icons.Filled.Palette, "Theme", themeLabel(state.theme.name), palette) { viewModel.cycleTheme() } }
                item { MobileSettingRow(Icons.Filled.Videocam, "Video quality", state.videoQuality.label, palette) { viewModel.cycleVideoQuality() } }
                item { MobileSettingRow(Icons.Filled.Lock, "Parental lock", if (state.parentalLock) "On" else "Off", palette) { viewModel.toggleParentalLock() } }
                item { MobileSettingRow(Icons.Filled.PlayArrow, "Playback", "ExoPlayer · HLS · adaptive", palette) {} }
                item { MobileSettingRow(Icons.Filled.Language, "Localization", "English · Europe/London", palette) {} }
                item { MobileSettingRow(Icons.Filled.Wifi, "Network", "Active · DoH fallback", palette) {} }
                item { MobileSettingRow(Icons.Filled.Refresh, "Reload portal", state.reloadStatus ?: "Tap to reconnect", palette) { viewModel.reloadPortal() } }
                item {
                    MobileSettingRow(Icons.Filled.SystemUpdateAlt, "Software update", updateStatus, palette) {
                        // Tapping while an update is offered installs it; otherwise re-check.
                        (updateState as? UpdateState.Available)?.let { updateVm.update(it.info) }
                            ?: updateVm.checkNow()
                    }
                }
                item { MobileSettingRow(Icons.Filled.CardMembership, "Refresh subscription", "Tap to refresh", palette) { viewModel.loadAccountInfo() } }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, palette: com.tenkultra.tv.presentation.theme.AppPalette, valueColor: Color = palette.text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = palette.textSecondary, fontSize = 14.sp, modifier = Modifier.width(130.dp))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MobileSettingRow(icon: ImageVector, title: String, subtitle: String, palette: com.tenkultra.tv.presentation.theme.AppPalette, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = palette.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = palette.textSecondary, fontSize = 13.sp)
            }
        }
    }
}

private fun themeLabel(name: String): String = when (name) {
    "BRAND" -> "Coffee"
    "CLASSIC" -> "Blue"
    "MODERN" -> "Netflix"
    else -> name
}
