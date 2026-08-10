package com.tenkultra.tv.presentation.screens.blocked

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * The STB-BLOCKED screen — shown when the portal rejects the box (blocked / subscription expired).
 * Per the provider's requirement, a blocked box must NOT stay usable: we surface the provider's
 * "Your STB is blocked. Call the provider." message so the customer knows WHY, then CLOSE the app.
 * Back or OK closes immediately; otherwise it auto-closes after a few seconds.
 */
@Composable
fun BlockedScreen(
    message: String?,
    onConnected: () -> Unit,
    viewModel: BlockedViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val context = LocalContext.current

    // Seed the provider's block message (from the nav arg), if any.
    LaunchedEffect(message) { viewModel.setInitialMessage(message) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Close the whole app — a blocked box is not allowed to keep running.
    fun exitApp() {
        (context as? Activity)?.finishAndRemoveTask()
    }

    // Auto-close after a short read time so the customer sees the reason first.
    LaunchedEffect(Unit) {
        delay(AUTO_CLOSE_MS)
        exitApp()
    }
    // Blocked box has nowhere to go — Back closes the app.
    BackHandler { exitApp() }

    val providerMessage = state.message?.takeIf { it.isNotBlank() } ?: message?.takeIf { it.isNotBlank() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1B2A))       // deep blue like the reference boot screen
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back, Key.Escape -> {
                        exitApp(); true
                    }
                    else -> false
                }
            }
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Your STB is blocked.",
                color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Call the provider.",
                color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (providerMessage != null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    providerMessage,
                    color = Color(0xFFB7C4D4), fontSize = 16.sp, textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Closing…",
                color = Color(0xFF8598AC), fontSize = 14.sp, textAlign = TextAlign.Center
            )
        }
    }
}

/** How long the block reason stays on screen before the app auto-closes. */
private const val AUTO_CLOSE_MS = 5000L
