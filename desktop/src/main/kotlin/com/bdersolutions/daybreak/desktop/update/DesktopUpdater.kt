package com.bdersolutions.daybreak.desktop.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration

/** The newest Windows build the update server reports. */
data class WindowsBuild(val url: String, val name: String, val size: Long?, val sha256: String?)

data class LatestRelease(
    val tag: String,
    val versionName: String,
    val versionCode: Int,
    val changelog: String,
    val msi: WindowsBuild,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: LatestRelease) : UpdateState
    data class Downloading(val release: LatestRelease, val done: Long, val total: Long) : UpdateState
    data class ReadyToInstall(val release: LatestRelease, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** The version this build was made with and where its update server is. */
data class BuildInfo(val versionName: String, val updateApiUrl: String) {
    val versionCode: Int get() = versionCodeOf(versionName)
    val updatesEnabled: Boolean get() = updateApiUrl.isNotBlank()

    companion object {
        /** Reads the file the Gradle build writes into the resources. Falls back to a dev build. */
        fun load(): BuildInfo {
            val properties = java.util.Properties()
            BuildInfo::class.java.getResourceAsStream("/daybreak-build.properties")?.use { properties.load(it) }
            return BuildInfo(
                versionName = properties.getProperty("versionName")?.takeIf { it.matches(VERSION) } ?: "1.0.0",
                updateApiUrl = properties.getProperty("updateApiUrl").orEmpty().trim().trimEnd('/'),
            )
        }

        private val VERSION = Regex("""\d+\.\d+\.\d+""")

        /** The same formula as the Android app and the update server: major*10000 + minor*100 + patch. */
        fun versionCodeOf(versionName: String): Int {
            val parts = versionName.split('.').map { it.toIntOrNull() ?: 0 }
            return (parts.getOrElse(0) { 0 }) * 10000 + (parts.getOrElse(1) { 0 }) * 100 + (parts.getOrElse(2) { 0 })
        }
    }
}

/**
 * Checks the update server for a newer Windows build, downloads its MSI and checks the SHA-256
 * before anything runs. The installer is started with msiexec; the app then closes itself.
 * The Android app has the same flow for APKs.
 */
class DesktopUpdater(
    val build: BuildInfo = BuildInfo.load(),
    private val directory: File = File(System.getProperty("java.io.tmpdir")),
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * Asks the server for the newest release and says whether it is newer than this build. With
     * [autoDownload] a newer build is downloaded and checked at once, in the background, so it is
     * ready to install when the person is asked. A check never runs over a download in progress.
     */
    suspend fun check(autoDownload: Boolean = false) {
        if (!build.updatesEnabled) {
            _state.value = UpdateState.Failed("Updates are not configured in this build.")
            return
        }
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        try {
            val release = fetchLatest()
            if (release.versionCode > build.versionCode) {
                _state.value = UpdateState.Available(release)
                if (autoDownload) scope.launch { download(release) }
            } else {
                _state.value = UpdateState.UpToDate
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: "The update check failed.")
        }
    }

    /**
     * Runs downloads outside the screens, so leaving a page does not cancel them. The app's own
     * scope lives as long as the app.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Downloads the MSI of [release] and checks it against the published SHA-256. */
    suspend fun download(release: LatestRelease) {
        _state.value = UpdateState.Downloading(release, 0, release.msi.size ?: 0)
        try {
            val file = withContext(Dispatchers.IO) { downloadVerified(release) }
            _state.value = UpdateState.ReadyToInstall(release, file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: "The download failed.")
        }
    }

    /** Starts the MSI with the Windows installer. The caller closes the app afterwards. */
    fun install(file: File) {
        ProcessBuilder("msiexec.exe", "/i", file.absolutePath).start()
    }

    private suspend fun fetchLatest(): LatestRelease = withContext(Dispatchers.IO) {
        val url = build.updateApiUrl + "/v1/latest"
        requireHttps(url)
        val response = client.send(
            HttpRequest.newBuilder(URI.create(url)).header("Accept", "application/json").GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        if (response.statusCode() != 200) throw IllegalStateException("The update server answered ${response.statusCode()}.")
        parseLatest(response.body())
    }

    private fun downloadVerified(release: LatestRelease): File {
        requireHttps(release.msi.url)
        val expected = release.msi.sha256 ?: throw IllegalStateException("The update server gave no checksum; nothing was installed.")
        val fileName = "Daybreak-${release.versionName.ifBlank { release.tag }}.msi"
        val target = File(directory, fileName)
        val part = File(directory, "$fileName.part")
        val response = client.send(
            HttpRequest.newBuilder(URI.create(release.msi.url)).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        if (response.statusCode() != 200) {
            response.body().close()
            throw IllegalStateException("The download answered ${response.statusCode()}.")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        response.body().use { input: InputStream ->
            part.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    total += read
                    _state.value = UpdateState.Downloading(release, total, release.msi.size ?: 0)
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        val size = release.msi.size
        if (size != null && size != total) {
            part.delete()
            throw IllegalStateException("The download has the wrong size; nothing was installed.")
        }
        if (actual != expected) {
            part.delete()
            throw IllegalStateException("The checksum does not match; nothing was installed.")
        }
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    private fun requireHttps(url: String) {
        require(url.startsWith("https://")) { "Updates are only downloaded over https." }
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString
}

/**
 * Reads the answer of the update server's /v1/latest. Kept apart from the network, so the contract
 * can be tested: the JSON in, the release out. Throws when a field the update needs is missing, so
 * a half-published release is never offered.
 */
internal fun parseLatest(body: String): LatestRelease {
    val json = JsonParser.parseString(body).asJsonObject
    val windows = json.get("windows")?.takeUnless { it.isJsonNull }?.asJsonObject
    val msi = windows?.get("msi")?.takeUnless { it.isJsonNull }?.asJsonObject
        ?: throw IllegalStateException("The newest release has no Windows build yet.")
    return LatestRelease(
        tag = json.optionalString("tag") ?: throw IllegalStateException("The update server sent no version."),
        versionName = json.optionalString("versionName").orEmpty(),
        versionCode = json.get("versionCode")?.takeUnless { it.isJsonNull }?.asInt
            ?: throw IllegalStateException("The update server sent no version code."),
        changelog = json.optionalString("changelog").orEmpty(),
        msi = WindowsBuild(
            url = msi.optionalString("url") ?: throw IllegalStateException("The Windows build has no address."),
            name = msi.optionalString("name").orEmpty(),
            size = msi.get("size")?.takeUnless { it.isJsonNull }?.asLong,
            sha256 = msi.optionalString("sha256")?.lowercase(),
        ),
    )
}

private fun JsonObject.optionalString(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.asString
