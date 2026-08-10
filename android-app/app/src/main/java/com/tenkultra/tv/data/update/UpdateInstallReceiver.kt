package com.tenkultra.tv.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/**
 * Receives status callbacks from [android.content.pm.PackageInstaller]. When the system needs
 * the user to confirm the install (the standard, non-system-app case), it sends
 * [PackageInstaller.STATUS_PENDING_USER_ACTION] carrying the confirm intent — we launch it so
 * the "Install this update?" dialog appears. On a system / device-owner box (the pre-installed
 * China boxes) the install completes silently and we just record the result.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // System needs the user to confirm — launch its "Install?" dialog.
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }
            PackageInstaller.STATUS_SUCCESS ->
                UpdateManager.active?.onInstallResult(true, null) // (app is usually replaced/relaunched anyway)
            else ->
                UpdateManager.active?.onInstallResult(
                    false,
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed ($status)."
                )
        }
    }

    companion object {
        const val ACTION = "com.tenkultra.tv.UPDATE_INSTALL_STATUS"
    }
}
