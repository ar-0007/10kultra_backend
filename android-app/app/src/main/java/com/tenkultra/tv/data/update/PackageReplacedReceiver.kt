package com.tenkultra.tv.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires once, right after THIS app is updated (ACTION_MY_PACKAGE_REPLACED). It relaunches the
 * app so an OTA update finishes seamlessly — on a silent-install-capable box (system /
 * preinstalled app) the user sees the app simply reopen on the new version, with no manual step.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launch) }
    }
}
