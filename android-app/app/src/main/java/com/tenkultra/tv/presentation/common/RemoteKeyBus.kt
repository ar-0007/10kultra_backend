package com.tenkultra.tv.presentation.common

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The four STB colour buttons, normalised so the UI reacts to the colour, not a raw keycode. */
enum class RemoteColor { RED, GREEN, YELLOW, BLUE }

/**
 * A tiny app-wide bus for the remote's COLOUR buttons.
 *
 * Colour/media keys are unreliable through Compose's `onPreviewKeyEvent` — if any child (the
 * muted preview PlayerView, a text field, …) holds focus the key never reaches the screen's
 * handler. So we intercept them once at [com.tenkultra.tv.MainActivity.dispatchKeyEvent] — the
 * earliest hook, before focus routing — normalise them here, and let whichever screen is on
 * screen collect [events]. D-pad/OK stay in the per-screen handlers as before.
 *
 * The map is a WIDE net on purpose: different STB / Android-TV remotes report the same physical
 * colour button under different keycodes. Every candidate that lands on a colour is folded to the
 * same [RemoteColor] so "the red button" just works across boxes.
 */
object RemoteKeyBus {
    // No replay: a colour press must not re-fire when a screen recomposes / re-subscribes.
    private val _events = MutableSharedFlow<RemoteColor>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    /** Maps a hardware keyCode to a colour button, or null when it isn't one. */
    fun colorFor(keyCode: Int): RemoteColor? = when (keyCode) {
        // Standard Android colour keycodes (183–186) — most STB/Android-TV remotes.
        KeyEvent.KEYCODE_PROG_RED,
        KeyEvent.KEYCODE_F1,
        KeyEvent.KEYCODE_MEDIA_RECORD, // many STB remotes put "red" on the record button
        403 -> RemoteColor.RED

        KeyEvent.KEYCODE_PROG_GREEN,
        KeyEvent.KEYCODE_F2,
        404 -> RemoteColor.GREEN

        KeyEvent.KEYCODE_PROG_YELLOW,
        KeyEvent.KEYCODE_F3,
        405 -> RemoteColor.YELLOW

        KeyEvent.KEYCODE_PROG_BLUE,
        KeyEvent.KEYCODE_F4,
        406 -> RemoteColor.BLUE

        else -> null
    }

    fun emit(color: RemoteColor) { _events.tryEmit(color) }
}
