package com.tenkultra.tv.presentation.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import com.tenkultra.tv.BuildConfig
import com.tenkultra.tv.data.update.UpdateState
import com.tenkultra.tv.presentation.common.UpdateViewModel
import com.tenkultra.tv.presentation.common.onTap
import com.tenkultra.tv.domain.model.AppLayout
import com.tenkultra.tv.domain.model.AppTheme
import com.tenkultra.tv.presentation.theme.appPalette
import com.tenkultra.tv.presentation.theme.classicRowBrush
import com.tenkultra.tv.util.RemoteConfig
import com.tenkultra.tv.presentation.theme.screenBackgroundBrush

private enum class SettingsItem(val label: String, val icon: ImageVector) {
    SUBSCRIPTION("Subscription", Icons.Filled.CardMembership),
    MAC_ADDRESS("MAC address", Icons.Filled.Fingerprint),
    CONNECT_MOBILE("Connect Android app", Icons.Filled.Smartphone),
    PARENTAL("Parental control", Icons.Filled.Lock),
    LOCALIZATION("Localization", Icons.Filled.Language),
    THEME("Theme", Icons.Filled.Palette),
    LAYOUT("Layout", Icons.Filled.ViewQuilt),
    SOFTWARE_UPDATE("Software update", Icons.Filled.SystemUpdateAlt),
    NETWORK_INFO("Network info", Icons.Filled.Info),
    VIDEO("Video", Icons.Filled.Videocam),
    AUDIO("Audio", Icons.Filled.VolumeUp),
    PLAYBACK("Playback", Icons.Filled.PlayArrow),
    NETWORK("Network", Icons.Filled.Wifi),
    REMOTE("Remote control", Icons.Filled.SettingsRemote),
    SERVERS("Servers", Icons.Filled.Dns),
    DEVICE_INFO("Device info", Icons.Filled.PhoneAndroid),
    CHANGE_PORTAL("Change portal URL", Icons.Filled.Link),
    RELOAD_PORTAL("Reload portal", Icons.Filled.Refresh),
    INNER_PORTAL("Go to the inner portal", Icons.AutoMirrored.Filled.OpenInNew),
    REBOOT("Reboot device", Icons.Filled.PowerSettingsNew)
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val palette = appPalette
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateVm: UpdateViewModel = hiltViewModel()
    val updateState by updateVm.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val items = SettingsItem.entries
    var index by remember { mutableIntStateOf(0) }
    var parentalDialog by remember { mutableStateOf(false) }
    var portalDialog by remember { mutableStateOf(false) }
    var rebootConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val anyDialog = parentalDialog || portalDialog || rebootConfirm

    LaunchedEffect(anyDialog) { if (!anyDialog) runCatching { focus.requestFocus() } }

    BackHandler {
        when {
            parentalDialog -> parentalDialog = false
            portalDialog -> portalDialog = false
            rebootConfirm -> rebootConfirm = false
            else -> onBack()
        }
    }

    fun act(item: SettingsItem) {
        when (item) {
            SettingsItem.THEME -> viewModel.cycleTheme()
            SettingsItem.LAYOUT -> viewModel.cycleLayout()
            SettingsItem.VIDEO -> viewModel.cycleVideoQuality()
            SettingsItem.PARENTAL -> parentalDialog = true
            SettingsItem.CHANGE_PORTAL -> { portalDialog = true; viewModel.startPortalQr() }
            SettingsItem.REBOOT -> rebootConfirm = true
            SettingsItem.RELOAD_PORTAL -> viewModel.reloadPortal()
            SettingsItem.SOFTWARE_UPDATE -> updateVm.checkNow()
            SettingsItem.SUBSCRIPTION -> viewModel.loadAccountInfo()
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBackgroundBrush(palette))
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                // While any dialog is open, let it own the keys.
                if (anyDialog) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { if (index > 0) index--; true }
                    Key.DirectionDown -> { if (index < items.lastIndex) index++; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { act(items[index]); true }
                    Key.Back -> { onBack(); true }
                    else -> false
                }
            }
            .padding(28.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Text("Settings", color = palette.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().weight(1f)) {
                SettingsList(
                    items = items,
                    selectedIndex = index,
                    onItemTap = { i -> index = i; act(items[i]) },
                    modifier = Modifier.width(420.dp).fillMaxHeight()
                )
                Spacer(Modifier.width(28.dp))
                val updateStatus = when (val u = updateState) {
                    UpdateState.Checking -> "Checking…"
                    UpdateState.UpToDate -> "Up to date"
                    is UpdateState.Available -> "Update available: ${u.info.versionName}"
                    is UpdateState.Downloading -> "Downloading… ${u.percent}%"
                    UpdateState.Installing -> "Installing…"
                    is UpdateState.Error -> u.message
                    UpdateState.Idle -> "—"
                }
                DetailPanel(
                    item = items[index],
                    state = state,
                    updateStatus = updateStatus,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }

        if (parentalDialog) {
            ParentalDialog(
                pinSet = state.parentalPinSet,
                lockOn = state.parentalLock,
                verifyPin = { viewModel.pinMatches(it) },
                onSetPin = { viewModel.setParentalPin(it) },
                onRemovePin = { viewModel.removeParentalPin() },
                onToggleLock = { viewModel.toggleParentalLock() },
                onClose = { parentalDialog = false }
            )
        }

        if (portalDialog) {
            // Auto-connect when the provider submits a URL via the dialog's QR.
            LaunchedEffect(state.qrReceivedUrl) {
                state.qrReceivedUrl?.let { url ->
                    portalDialog = false
                    viewModel.consumeQrUrl()
                    viewModel.changePortal(url) { restartApp(context) }
                }
            }
            PortalChangeDialog(
                current = state.portalUrl,
                qrCode = state.portalQrCode,
                qrData = if (state.portalQrCode.isNotBlank())
                    RemoteConfig.setupUrl(state.portalQrCode, state.mac) else "",
                onRefreshQr = viewModel::refreshPortalQr,
                onConnect = { url ->
                    portalDialog = false
                    viewModel.changePortal(url) { restartApp(context) }
                },
                onClose = { viewModel.stopPortalQr(); portalDialog = false }
            )
        }

        if (rebootConfirm) {
            RebootDialog(
                onConfirm = { rebootConfirm = false; rebootDevice(context) },
                onClose = { rebootConfirm = false }
            )
        }
    }
}

/**
 * Best-effort device reboot. Works on system / pre-installed boxes that hold the REBOOT permission
 * (or are rooted). A normal installed app can't reboot the OS, so we fall back to restarting the app.
 */
private fun rebootDevice(context: Context) {
    // 1) System reboot — granted only to system/privileged apps (the China boxes).
    val viaPm = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.reboot(null); true
    }.getOrDefault(false)
    if (viaPm) return
    // 2) Rooted boxes: shell reboot.
    val viaSu = runCatching {
        Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot")); true
    }.getOrDefault(false)
    if (viaSu) return
    // 3) Can't reboot the OS — restart the app instead.
    restartApp(context)
}

/** Relaunches the app from scratch (used after a portal change, and as the reboot fallback). */
private fun restartApp(context: Context) {
    runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
    }
    Runtime.getRuntime().exit(0)
}

@Composable
private fun SettingsList(
    items: List<SettingsItem>,
    selectedIndex: Int,
    onItemTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = appPalette
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem((selectedIndex - 4).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        itemsIndexed(items) { i, item ->
            val selected = i == selectedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(classicRowBrush(palette, selected))
                    .then(if (selected) Modifier.border(1.dp, palette.primary, RoundedCornerShape(8.dp)) else Modifier)
                    .onTap(i) { onItemTap(i) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    item.icon, null,
                    tint = if (selected) palette.background else palette.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    item.label,
                    color = if (selected) palette.background else palette.text,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun DetailPanel(
    item: SettingsItem,
    state: SettingsUiState,
    updateStatus: String,
    modifier: Modifier = Modifier
) {
    val palette = appPalette
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface.copy(alpha = 0.6f))
            .padding(24.dp)
    ) {
        Text(item.label, color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        when (item) {
            SettingsItem.SUBSCRIPTION -> {
                if (state.accountLoading) {
                    Text("Loading account…", color = palette.textSecondary, fontSize = 14.sp)
                } else {
                    Row { Label("Registered to"); Value(state.accountName) }
                    Row { Label("Package"); Value(state.accountPackage) }
                    Row { Label("Type"); Value(if (state.accountIsTrial) "Trial" else "Paid") }
                    Row { Label("App version"); Value(BuildConfig.VERSION_NAME) }
                    Row {
                        Label("Status")
                        Text(
                            state.accountStatus,
                            color = if (state.accountStatus == "Active") Color(0xFF5FBF8F) else Color(0xFFD9776A),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    Row { Label("Expires on"); Value(state.accountExpiry) }
                    state.accountDaysLeft?.let { d ->
                        Row {
                            Label("Time remaining")
                            Text(
                                if (d >= 0) "$d days left" else "Expired ${-d} days ago",
                                color = when {
                                    d < 0 -> Color(0xFFD9776A)
                                    d <= 7 -> Color(0xFFE0A458)
                                    else -> palette.text
                                },
                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                    if (state.accountPhone != "—") Row { Label("Account note"); Value(state.accountPhone) }
                    if (state.accountName == "—" && state.accountPackage == "—") {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Subscription details load from the portal once it's connected " +
                                "and the account is active.",
                            color = palette.textSecondary, fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Press OK to refresh from the portal.", color = palette.primary, fontSize = 14.sp)
                }
            }
            SettingsItem.PARENTAL -> {
                Row { Label("PIN"); Value(if (state.parentalPinSet) "Set ••••" else "Not set") }
                Row { Label("Adult-content lock"); Value(if (state.parentalLock) "On" else "Off") }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Protect kids: set a 4-digit PIN and turn on the lock to hide adult " +
                        "categories (18+/XXX/Adult) across Movies, Series and Live TV.",
                    color = palette.textSecondary, fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                Text("Press OK to manage parental controls.", color = palette.primary, fontSize = 14.sp)
            }
            SettingsItem.THEME -> {
                Row { Label("Current theme"); Value(themeName(state.theme)) }
                Spacer(Modifier.height(10.dp))
                Text("Press OK to switch theme.", color = palette.primary, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                AppTheme.entries.forEach { t ->
                    Text(
                        text = (if (t == state.theme) "●  " else "○  ") + themeName(t),
                        color = if (t == state.theme) palette.primary else palette.textSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            SettingsItem.LAYOUT -> {
                Row { Label("Current layout"); Value(layoutName(state.layout)) }
                Spacer(Modifier.height(10.dp))
                Text("Press OK to switch layout.", color = palette.primary, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                AppLayout.entries.forEach { l ->
                    Text(
                        text = (if (l == state.layout) "●  " else "○  ") + layoutName(l),
                        color = if (l == state.layout) palette.primary else palette.textSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            SettingsItem.NETWORK_INFO -> {
                Row { Label("Portal URL"); Value(state.portalUrl.ifEmpty { "—" }) }
                Row { Label("MAC address"); Value(state.mac.ifEmpty { "—" }) }
                Row { Label("Device ID"); Value(state.deviceId.take(24) + "…") }
                Row { Label("Status"); Value("Connected") }
            }
            SettingsItem.DEVICE_INFO -> {
                Row { Label("Model"); Value("MAG250 (emulated)") }
                Row { Label("MAC address"); Value(state.mac.ifEmpty { "—" }) }
                Row { Label("App version"); Value(BuildConfig.VERSION_NAME) }
                Row { Label("Device ID"); Value(state.deviceId.take(24) + "…") }
            }
            SettingsItem.MAC_ADDRESS -> {
                Text("This box's MAC address", color = palette.textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 22.dp, vertical = 16.dp)
                ) {
                    Text(
                        state.mac.ifEmpty { "—" },
                        color = palette.primary, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Give this MAC to your IPTV provider so they can whitelist this box on their panel.",
                    color = palette.textSecondary, fontSize = 14.sp, lineHeight = 20.sp
                )
            }
            SettingsItem.CONNECT_MOBILE -> {
                Text(
                    "Scan this with the 10K Ultra app on your phone to copy this box's portal + MAC — " +
                        "your phone connects to the same account, one tap.",
                    color = palette.textSecondary, fontSize = 14.sp, lineHeight = 20.sp
                )
                Spacer(Modifier.height(16.dp))
                if (state.portalUrl.isBlank()) {
                    Text("Connect a portal first, then a QR appears here.", color = palette.textSecondary, fontSize = 14.sp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.text)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // On-device QR (no backend image) → always renders, works offline.
                        com.tenkultra.tv.presentation.common.QrCode(
                            data = RemoteConfig.mobileConnectLink(state.portalUrl, state.mac),
                            sizeDp = 196.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            SettingsItem.VIDEO -> {
                Row { Label("Video quality"); Value(state.videoQuality.label) }
                Spacer(Modifier.height(10.dp))
                Text("Press OK to change the max streaming quality.", color = palette.primary, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                com.tenkultra.tv.domain.model.VideoQuality.entries.forEach { q ->
                    Text(
                        text = (if (q == state.videoQuality) "●  " else "○  ") + q.label,
                        color = if (q == state.videoQuality) palette.primary else palette.textSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            SettingsItem.RELOAD_PORTAL -> {
                Text("Reconnect to the portal (re-handshake).", color = palette.textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Text("Press OK to reload.", color = palette.primary, fontSize = 14.sp)
                state.reloadStatus?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            SettingsItem.LOCALIZATION -> {
                Row { Label("Language"); Value("English") }
                Row { Label("Time zone"); Value("Europe/London") }
                Row { Label("Clock format"); Value("24-hour") }
                Spacer(Modifier.height(10.dp))
                Text("The portal serves content metadata in English.", color = palette.textSecondary, fontSize = 14.sp)
            }
            SettingsItem.AUDIO -> {
                Row { Label("Output"); Value("Auto (HDMI / SPDIF)") }
                Row { Label("Preferred track"); Value("Original") }
                Row { Label("Volume"); Value("System") }
                Spacer(Modifier.height(10.dp))
                Text("Audio passes through to your TV/AVR; control volume with your TV remote.",
                    color = palette.textSecondary, fontSize = 14.sp)
            }
            SettingsItem.PLAYBACK -> {
                Row { Label("Engine"); Value("ExoPlayer (HLS)") }
                Row { Label("Buffering"); Value("Adaptive") }
                Row { Label("Auto-resume"); Value("On") }
                Spacer(Modifier.height(10.dp))
                Text("Max quality is set under Video. Streams use HLS with adaptive buffering.",
                    color = palette.textSecondary, fontSize = 14.sp)
            }
            SettingsItem.NETWORK -> {
                Row { Label("Connection"); Value("Active") }
                Row { Label("DNS"); Value("System + DoH fallback") }
                Row { Label("Redirect fix"); Value("On") }
                Spacer(Modifier.height(10.dp))
                Text("DNS-over-HTTPS fallback keeps dynamic-DNS portals reachable.",
                    color = palette.textSecondary, fontSize = 14.sp)
            }
            SettingsItem.REMOTE -> {
                Row { Label("D-pad"); Value("Navigate") }
                Row { Label("OK / Center"); Value("Select / Play") }
                Row { Label("Back"); Value("Go back") }
                Spacer(Modifier.height(10.dp))
                Text("Touch is also supported on mobile/touch devices.",
                    color = palette.textSecondary, fontSize = 14.sp)
            }
            SettingsItem.SERVERS -> {
                Row { Label("Portal"); Value(state.portalUrl.ifEmpty { "—" }) }
                Row { Label("Device ID"); Value(state.deviceId.take(20) + "…") }
                Row { Label("STB type"); Value("MAG250") }
            }
            SettingsItem.SOFTWARE_UPDATE -> {
                Row { Label("App version"); Value(BuildConfig.VERSION_NAME) }
                Row { Label("Status"); Value(updateStatus) }
                Spacer(Modifier.height(10.dp))
                Text("Press OK to check for updates now.", color = palette.primary, fontSize = 14.sp)
            }
            SettingsItem.CHANGE_PORTAL -> {
                Text("Current portal", color = palette.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(state.portalUrl.ifBlank { "—" }, color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                Text("Press OK to enter a different portal URL and reconnect.", color = palette.primary, fontSize = 14.sp)
            }
            SettingsItem.REBOOT -> {
                Text("Press OK to restart the device.", color = palette.primary, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text("On boxes that don't allow a full reboot, the app restarts instead.", color = palette.textSecondary, fontSize = 13.sp)
            }
            SettingsItem.INNER_PORTAL -> {
                Text("Not applicable in this app.", color = palette.textSecondary, fontSize = 14.sp)
            }
            else -> {
                Text(
                    "This section is managed by the portal.",
                    color = palette.textSecondary, fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Change the portal URL — TWO ways (same as the setup screen): type it manually on the left, OR
 * scan the QR on the right (provider enters the URL in a browser → the box auto-connects). A
 * "Refresh QR" button regenerates the code. On Connect the app saves it and restarts to reconnect.
 */
@Composable
private fun PortalChangeDialog(
    current: String,
    qrCode: String,
    qrData: String,
    onRefreshQr: () -> Unit,
    onConnect: (String) -> Unit,
    onClose: () -> Unit
) {
    val palette = appPalette
    var url by remember { mutableStateOf(current) }
    var focused by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Back) { onClose(); true } else false },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .width(760.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .border(1.dp, palette.primary, RoundedCornerShape(14.dp))
                .padding(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ---- Left: manual URL entry ----
            Column(Modifier.weight(1f).padding(end = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Link, null, tint = palette.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Change Portal URL", color = palette.text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (focused) palette.background else palette.background.copy(alpha = 0.6f))
                        .border(
                            if (focused) 2.dp else 1.dp,
                            if (focused) palette.primary else palette.primary.copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(palette.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrect = false
                        ),
                        keyboardActions = KeyboardActions(onGo = { if (url.isNotBlank()) onConnect(url) }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(field)
                            .onFocusChanged { focused = it.isFocused },
                        decorationBox = { inner ->
                            if (url.isEmpty()) Text("http://your-portal.com/c/", color = palette.textSecondary, fontSize = 15.sp)
                            inner()
                        }
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DialogButton(text = "Connect", modifier = Modifier.weight(1f)) { if (url.isNotBlank()) onConnect(url) }
                    DialogButton(text = "Cancel", modifier = Modifier.weight(1f)) { onClose() }
                }
            }

            // ---- Right: QR pairing ----
            Column(
                modifier = Modifier.width(220.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Or scan from your phone", color = palette.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.text)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrData.isNotBlank()) {
                        com.tenkultra.tv.presentation.common.QrCode(
                            data = qrData, sizeDp = 160.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (qrCode.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(qrCode, color = palette.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                }
                Spacer(Modifier.height(10.dp))
                DialogButton(text = "Refresh QR") { onRefreshQr() }
            }
        }
    }
}

/** Confirmation before rebooting/restarting the device. */
@Composable
private fun RebootDialog(onConfirm: () -> Unit, onClose: () -> Unit) {
    val palette = appPalette
    val yes = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { yes.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Back) { onClose(); true } else false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .border(1.dp, palette.primary, RoundedCornerShape(14.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.PowerSettingsNew, null, tint = palette.primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(12.dp))
            Text("Reboot device?", color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("The device will restart now.", color = palette.textSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton(text = "Reboot", focus = yes, modifier = Modifier.weight(1f)) { onConfirm() }
                DialogButton(text = "Cancel", modifier = Modifier.weight(1f)) { onClose() }
            }
        }
    }
}

private enum class PinMode { CREATE, VERIFY, MANAGE }

@Composable
private fun ParentalDialog(
    pinSet: Boolean,
    lockOn: Boolean,
    verifyPin: suspend (String) -> Boolean,
    onSetPin: (String) -> Unit,
    onRemovePin: () -> Unit,
    onToggleLock: () -> Unit,
    onClose: () -> Unit
) {
    val palette = appPalette
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(if (pinSet) PinMode.VERIFY else PinMode.CREATE) }
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var lock by remember { mutableStateOf(lockOn) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(mode) {
        pin = ""
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) { onClose(); true }
                else false
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .border(1.dp, palette.primary, RoundedCornerShape(14.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = palette.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Parental Control", color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))

            when (mode) {
                PinMode.CREATE, PinMode.VERIFY -> {
                    Text(
                        when {
                            mode == PinMode.VERIFY -> "Enter your 4-digit PIN"
                            pinSet -> "Enter a new 4-digit PIN"
                            else -> "Set a 4-digit PIN to protect kids"
                        },
                        color = palette.textSecondary, fontSize = 15.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    PinField(pin = pin, focus = firstFocus) { pin = it; message = null }
                    message?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = palette.primary, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        DialogButton(
                            text = if (mode == PinMode.VERIFY) "Unlock" else "Save",
                            modifier = Modifier.weight(1f)
                        ) {
                            if (pin.length != 4) { message = "Enter 4 digits"; return@DialogButton }
                            if (mode == PinMode.VERIFY) {
                                scope.launch {
                                    if (verifyPin(pin)) { mode = PinMode.MANAGE } else { message = "Wrong PIN"; pin = "" }
                                }
                            } else {
                                onSetPin(pin); message = null; mode = PinMode.MANAGE
                            }
                        }
                        DialogButton(text = "Cancel", modifier = Modifier.weight(1f)) { onClose() }
                    }
                }
                PinMode.MANAGE -> {
                    Row { Label("Adult-content lock"); Value(if (lock) "On" else "Off") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "When on, categories named 18+ / XXX / Adult are hidden everywhere.",
                        color = palette.textSecondary, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DialogButton(
                            text = if (lock) "Turn lock off" else "Turn lock on",
                            focus = firstFocus,
                            modifier = Modifier.weight(1f)
                        ) { onToggleLock(); lock = !lock }
                        DialogButton(text = "Change PIN", modifier = Modifier.weight(1f)) { mode = PinMode.CREATE }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DialogButton(text = "Remove PIN", modifier = Modifier.weight(1f)) { onRemovePin(); onClose() }
                        DialogButton(text = "Done", modifier = Modifier.weight(1f)) { onClose() }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinField(pin: String, focus: FocusRequester, onChange: (String) -> Unit) {
    val palette = appPalette
    BasicTextField(
        value = pin,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        singleLine = true,
        textStyle = TextStyle(color = palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(palette.primary),
        visualTransformation = PasswordVisualTransformation('●'),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = Modifier
            .width(180.dp)
            .height(54.dp)
            .focusRequester(focus)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.background)
            .border(1.dp, palette.primary, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (pin.isEmpty()) Text("● ● ● ●", color = palette.textSecondary, fontSize = 20.sp)
                inner()
            }
        }
    )
}

@Composable
private fun DialogButton(
    text: String,
    modifier: Modifier = Modifier,
    focus: FocusRequester? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = (if (focus != null) modifier.focusRequester(focus) else modifier).height(48.dp)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Label(text: String) {
    val palette = appPalette
    Text(
        "$text:",
        color = palette.textSecondary,
        fontSize = 14.sp,
        modifier = Modifier.width(140.dp).padding(vertical = 4.dp)
    )
}

@Composable
private fun Value(text: String) {
    val palette = appPalette
    Text(text, color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(vertical = 4.dp))
}

private fun themeName(theme: AppTheme): String = when (theme) {
    AppTheme.BRAND -> "Coffee (Brand)"
    AppTheme.CLASSIC -> "Classic (STBEMU Blue)"
    AppTheme.MODERN -> "Modern (Netflix)"
}

private fun layoutName(layout: AppLayout): String = when (layout) {
    AppLayout.CLASSIC -> "Classic (STBEMU carousel)"
    AppLayout.MODERN -> "Modern (Sidebar + grid)"
    AppLayout.CINEMATIC -> "Cinematic (Hero + rails)"
}
