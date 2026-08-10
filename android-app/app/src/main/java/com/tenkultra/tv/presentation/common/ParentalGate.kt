package com.tenkultra.tv.presentation.common

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.CompositionLocalProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tenkultra.tv.data.parental.ParentalControl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The app-wide CHILD LOCK gate. Hosted once around the whole NavHost, so EVERY layout
 * (Classic / Modern / Cinematic / Mobile) gates adult content the same way.
 *
 * The lock is CATEGORY-level: an adult CATEGORY / GENRE (Live TV or Video Club) asks for the
 * parental PIN when it is opened; the channels and movies INSIDE it are then free to play, since
 * the user already proved they're the parent on the way in.
 *
 * Screens use it through [LocalParentalGate]:
 *   val gate = LocalParentalGate.current
 *   gate.guard(category.title) { open(category) }     // PIN first if adult, else straight through
 *   if (gate.isRestricted(category.title)) { …lock badge… }
 */
@Immutable
class ParentalGateController(
    /** True while adult content is locked (lock on + no PIN entered this session). */
    val restrictionActive: Boolean,
    private val isAdult: (String?) -> Boolean,
    private val request: (String?, () -> Unit) -> Unit
) {
    /** True if this title must not be shown/played without the PIN right now. */
    fun isRestricted(title: String?): Boolean = restrictionActive && isAdult(title)

    /** Runs [action] immediately, or asks for the parental PIN first when [title] is adult. */
    fun guard(title: String?, action: () -> Unit) {
        if (isRestricted(title)) request(title, action) else action()
    }
}

val LocalParentalGate = staticCompositionLocalOf {
    // Fallback for previews / screens rendered outside the host: never blocks, never crashes.
    ParentalGateController(restrictionActive = false, isAdult = { false }, request = { _, run -> run() })
}

@HiltViewModel
class ParentalGateViewModel @Inject constructor(
    private val parental: ParentalControl
) : ViewModel() {

    // Starts "locked" so adult content can never flash through while DataStore is still loading.
    val restrictionActive: StateFlow<Boolean> =
        parental.restrictionActiveFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun isAdult(title: String?): Boolean = parental.isAdultTitle(title)

    suspend fun verify(pin: String): Boolean = parental.verify(pin)
}

/** Wraps the app UI, providing [LocalParentalGate] and drawing the PIN dialog above everything. */
@Composable
fun ParentalGateHost(content: @Composable () -> Unit) {
    val viewModel: ParentalGateViewModel = hiltViewModel()
    val restricted by viewModel.restrictionActive.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingTitle by remember { mutableStateOf<String?>(null) }

    val controller = remember(restricted) {
        ParentalGateController(
            restrictionActive = restricted,
            isAdult = viewModel::isAdult,
            request = { title, action -> pendingTitle = title; pending = action }
        )
    }

    // The PIN dialog takes remote focus while it is up. When it closes (unlocked OR cancelled) the
    // focus has to go BACK into the screen underneath, otherwise the D-pad goes dead until Back.
    val contentFocus = remember { FocusRequester() }
    var dialogWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(pending) {
        if (pending != null) dialogWasOpen = true
        else if (dialogWasOpen) { dialogWasOpen = false; runCatching { contentFocus.requestFocus() } }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(contentFocus)
                .focusGroup()
                // While the PIN dialog is up, the screen underneath must not react to the remote
                // (otherwise D-pad presses keep moving the hidden list behind the dialog).
                // BACK always closes the dialog, so the user can never get stuck behind it.
                .onPreviewKeyEvent { event ->
                    if (pending == null) return@onPreviewKeyEvent false
                    if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                        pending = null
                        pendingTitle = null
                    }
                    true
                }
        ) {
            CompositionLocalProvider(LocalParentalGate provides controller) { content() }
        }

        pending?.let { action ->
            PinGateDialog(
                title = pendingTitle?.takeIf { it.isNotBlank() }?.let { "Locked: $it" }
                    ?: "Adult content locked",
                onVerify = { pin -> viewModel.verify(pin) },
                onSuccess = { pending = null; pendingTitle = null; action() },
                onCancel = { pending = null; pendingTitle = null }
            )
        }
    }
}
