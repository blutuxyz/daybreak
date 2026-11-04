package com.bdersolutions.daybreak.data.update

import android.content.Context
import android.content.pm.PackageInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * The update flow in one place: ask the server for a newer version, download its APK (resuming
 * if interrupted, and checking size and SHA-256), and hand it to the installer.
 *
 * Downloads run in this class's own scope, so leaving the Updates screen does not stop them.
 * [api] is null when no update server is configured; then every call does nothing.
 */
class UpdateManager(
    private val context: Context,
    private val api: UpdateApi?,
    private val store: UpdateStore,
    val installer: ApkInstaller = ApkInstaller(context),
) {
    val isConfigured: Boolean get() = api != null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private val updatesDir = File(context.cacheDir, "updates")

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _releases = MutableStateFlow(store.releases())

    /** The release history with changelogs. Starts with what was fetched last time. */
    val releases: StateFlow<List<ReleaseInfo>> = _releases.asStateFlow()

    private val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    val currentVersionCode: Long = packageInfo?.longVersionCode ?: 0L
    val currentVersionName: String = packageInfo?.versionName.orEmpty().ifBlank { "–" }

    /** When the server was last asked, in epoch milliseconds; 0 if never. */
    val lastCheckAt: Long get() = store.lastCheckAt

    /** Whether it is time to look again without the user asking. */
    fun isCheckDue(now: Long = System.currentTimeMillis()): Boolean =
        isConfigured && now - store.lastCheckAt > CHECK_INTERVAL_MS

    /** Asks the server for the newest release and updates [state]. */
    suspend fun check(): UpdateState {
        val api = api ?: return _state.value
        val busy = _state.value
        if (busy is UpdateState.Downloading || busy is UpdateState.Installing) return busy

        _state.value = UpdateState.Checking
        val latest = try {
            api.latest()
        } catch (error: IOException) {
            return fail(UpdateFailure.CHECK, null, error.message)
        }
        val now = System.currentTimeMillis()
        store.lastCheckAt = now

        val apk = latest?.apk
        if (latest == null || apk == null || latest.versionCode <= currentVersionCode) {
            // Up to date: whatever was downloaded for an earlier update is no longer needed.
            deleteDownloads(except = null)
            return setState(UpdateState.UpToDate(now))
        }
        val file = apkFile(latest)
        if (file.exists() && withContext(Dispatchers.IO) { matches(file, apk) }) {
            return setState(UpdateState.Ready(latest, file))
        }
        file.delete()
        return setState(UpdateState.Available(latest))
    }

    /** Refreshes the history shown under "Changes". Quietly keeps the old list when offline. */
    suspend fun refreshReleases() {
        val api = api ?: return
        val fresh = runCatching { api.releases() }.getOrNull() ?: return
        if (fresh.isEmpty()) return
        store.saveReleases(fresh)
        _releases.value = fresh
    }

    /** Downloads the update. Returns when it is done, failed, or cancelled. */
    suspend fun download() {
        val api = api ?: return
        val release = when (val current = _state.value) {
            is UpdateState.Available -> current.release
            is UpdateState.Failed -> current.release?.takeIf { current.failure != UpdateFailure.INSTALL }
            else -> null
        } ?: return
        val apk = release.apk ?: return

        updatesDir.mkdirs()
        val target = apkFile(release)
        val part = File(updatesDir, target.name + ".part")
        _state.value = UpdateState.Downloading(release, part.length(), apk.size)
        try {
            withContext(Dispatchers.IO) {
                // Reading is blocking, so the loop asks the job whether it was cancelled.
                val job = currentCoroutineContext()[Job]
                fetch(api, release, apk, part) { job?.isActive != false }
            }
            if (apk.size > 0 && part.length() != apk.size) {
                part.delete()
                fail(UpdateFailure.VERIFY, release, "wrong size")
                return
            }
            if (apk.sha256 != null && !sha256Of(part).equals(apk.sha256, ignoreCase = true)) {
                part.delete()
                fail(UpdateFailure.VERIFY, release, "wrong checksum")
                return
            }
            if (!part.renameTo(target)) {
                fail(UpdateFailure.DOWNLOAD, release, "could not save the file")
                return
            }
            deleteDownloads(except = target)
            setState(UpdateState.Ready(release, target))
        } catch (cancelled: CancellationException) {
            // Keep the partial file: the next download resumes from it.
            setState(UpdateState.Available(release))
            throw cancelled
        } catch (error: IOException) {
            fail(UpdateFailure.DOWNLOAD, release, error.message)
        }
    }

    /** Starts [download] in the background and returns at once. */
    fun downloadInBackground() {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch { download() }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    /** Hands the downloaded APK to the installer. The system asks the user to confirm. */
    fun install() {
        val ready = _state.value as? UpdateState.Ready ?: return
        _state.value = UpdateState.Installing(ready.release)
        scope.launch {
            try {
                installer.install(ready.file)
            } catch (error: IOException) {
                fail(UpdateFailure.INSTALL, ready.release, error.message)
            }
        }
    }

    /** Called by the install receiver when Android reports that the install did not happen. */
    fun onInstallFailed(status: Int, message: String?) {
        val release = (_state.value as? UpdateState.Installing)?.release
            ?: (_state.value as? UpdateState.Ready)?.release
            ?: return
        // Android reports a refusal by the package verifier (Google Play Protect) as "aborted", just
        // like a dismissed dialog. The message tells them apart.
        val verifierRefused = message?.contains("VERIFICATION", ignoreCase = true) == true
        if (status == PackageInstaller.STATUS_FAILURE_ABORTED && !verifierRefused) {
            // The user dismissed the confirmation: the APK is still there to try again.
            setState(UpdateState.Ready(release, apkFile(release)))
        } else {
            fail(UpdateFailure.INSTALL, release, if (verifierRefused) VERIFIER_REFUSED else message)
        }
    }

    /**
     * The release to announce after an update, or null. The first run after installing a newer
     * version shows what changed. A fresh install announces nothing.
     */
    suspend fun whatsNew(): ReleaseInfo? {
        if (!isConfigured) return null
        val seen = store.lastSeenVersionCode
        if (seen == 0L) {
            store.lastSeenVersionCode = currentVersionCode
            return null
        }
        if (currentVersionCode <= seen) return null

        var release = store.releases().firstOrNull { it.versionCode == currentVersionCode }
        if (release == null) {
            refreshReleases()
            release = _releases.value.firstOrNull { it.versionCode == currentVersionCode }
        }
        if (release == null || release.changelog.isBlank()) {
            store.lastSeenVersionCode = currentVersionCode
            return null
        }
        return release
    }

    fun markWhatsNewSeen() {
        store.lastSeenVersionCode = currentVersionCode
    }

    /** True the first time a notification is due for [release]; remembers that it was sent. */
    fun shouldNotify(release: ReleaseInfo): Boolean {
        if (store.notifiedVersionCode >= release.versionCode) return false
        store.notifiedVersionCode = release.versionCode
        return true
    }

    private fun fetch(
        api: UpdateApi,
        release: ReleaseInfo,
        apk: ApkInfo,
        part: File,
        isActive: () -> Boolean,
    ) {
        var offset = if (part.exists() && (apk.size <= 0 || part.length() < apk.size)) part.length() else 0L
        if (offset == 0L) part.delete()
        var response = api.openApk(release, offset)
        if (response.code == 416) {
            // The partial file is no use any more: start again.
            response.close()
            part.delete()
            offset = 0L
            response = api.openApk(release, 0L)
        }
        response.use {
            if (!response.isSuccessful) throw UpdateException("The update server answered ${response.code}")
            val body = response.body ?: throw UpdateException("The update server sent no file")
            // 206 continues the partial file; a plain 200 means the server sent it all again.
            val append = response.code == 206 && offset > 0
            if (!append) {
                part.delete()
                offset = 0L
            }
            val total = if (apk.size > 0) apk.size else offset + body.contentLength().coerceAtLeast(0L)
            var done = offset
            var lastEmit = 0L
            FileOutputStream(part, append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (!isActive()) throw CancellationException("download cancelled")
                        val now = System.nanoTime()
                        if (now - lastEmit > PROGRESS_INTERVAL_NS) {
                            lastEmit = now
                            _state.value = UpdateState.Downloading(release, done, total)
                        }
                    }
                }
            }
            _state.value = UpdateState.Downloading(release, done, total)
        }
    }

    private fun matches(file: File, apk: ApkInfo): Boolean =
        (apk.size <= 0 || file.length() == apk.size) &&
            (apk.sha256 == null || sha256Of(file).equals(apk.sha256, ignoreCase = true))

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun apkFile(release: ReleaseInfo) = File(updatesDir, "daybreak-${release.tag}.apk")

    private fun deleteDownloads(except: File?) {
        updatesDir.listFiles()?.forEach { file -> if (file != except) file.delete() }
    }

    private fun setState(state: UpdateState): UpdateState {
        _state.value = state
        return state
    }

    private fun fail(failure: UpdateFailure, release: ReleaseInfo?, detail: String?): UpdateState =
        setState(UpdateState.Failed(failure, release, detail))

    companion object {
        /** The detail of a [UpdateFailure.INSTALL] that the package verifier caused. */
        const val VERIFIER_REFUSED = "verifier"

        private const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
        private const val BUFFER_SIZE = 32 * 1024
        private const val PROGRESS_INTERVAL_NS = 150_000_000L
    }
}
