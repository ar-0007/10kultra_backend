package com.tenkultra.tv.presentation.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Adds touch-tap handling to an item WITHOUT making it focusable — so it works on
 * phones/touch screens while the existing D-pad (onPreviewKeyEvent) navigation on the
 * root stays intact. [key] should be the item's index/id so the gesture re-binds.
 */
fun Modifier.onTap(key: Any?, onTap: () -> Unit): Modifier =
    this.pointerInput(key) { detectTapGestures(onTap = { onTap() }) }
