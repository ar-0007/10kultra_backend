package com.tenkultra.tv.presentation.common

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** True on Android TV / leanback boxes (D-pad UI); false on phones & tablets (touch UI). */
@Composable
fun rememberIsTv(): Boolean {
    val context = LocalContext.current
    return remember {
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}
