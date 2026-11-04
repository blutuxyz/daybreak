package com.bdersolutions.daybreak.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.bdersolutions.daybreak.DaybreakApplication
import java.io.File
import java.io.IOException

/**
 * Installs a downloaded APK over the running app through Android's PackageInstaller.
 *
 * Android never installs silently: the first time the user must allow Daybreak to install apps
 * ([canInstall]), and each update asks for a confirmation. The new APK must be signed with the same
 * key as the installed app, or Android refuses it.
 */
class ApkInstaller(private val context: Context) {
    /** Whether the user has allowed this app to install packages. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The system screen where the user grants that permission. */
    fun permissionSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Streams [file] into an install session and commits it. The outcome arrives later at
     * [InstallResultReceiver]. Throws [IOException] if the session cannot be written.
     */
    fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                // Lets Android skip the confirmation for later updates where it allows that.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("daybreak.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val result = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
                // Mutable, because the system adds the install status to it.
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    result,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (error: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw if (error is IOException) error else IOException(error.message, error)
        }
    }
}

/** Receives the result of an install session: shows the system's confirmation, or reports failure. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm != null) context.startActivity(confirm)
            }

            // The app is replaced and restarted by Android; there is nothing to do here.
            PackageInstaller.STATUS_SUCCESS -> Unit

            else -> {
                val application = context.applicationContext as? DaybreakApplication
                application?.container?.updateManager?.onInstallFailed(status, message)
            }
        }
    }
}
