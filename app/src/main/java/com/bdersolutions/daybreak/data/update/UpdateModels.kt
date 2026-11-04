package com.bdersolutions.daybreak.data.update

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException

/** The APK of a release: where to get it, how big it is, and its SHA-256 (lower-case hex). */
data class ApkInfo(
    val url: String,
    val size: Long,
    val sha256: String?,
)

/** One release as the update server describes it. [apk] is null while no APK is attached. */
data class ReleaseInfo(
    val tag: String,
    val versionName: String,
    val versionCode: Long,
    val publishedAt: String?,
    /** The release notes, as Markdown. */
    val changelog: String,
    val apk: ApkInfo?,
)

/** What went wrong, so the screen can say it in words. */
enum class UpdateFailure { CHECK, DOWNLOAD, VERIFY, INSTALL }

/** Where the app is in the update flow. */
sealed interface UpdateState {
    /** Nothing checked yet in this run. */
    data object Idle : UpdateState

    data object Checking : UpdateState

    data class UpToDate(val checkedAt: Long) : UpdateState

    /** A newer version exists and is not downloaded. */
    data class Available(val release: ReleaseInfo) : UpdateState

    data class Downloading(val release: ReleaseInfo, val downloaded: Long, val total: Long) : UpdateState {
        /** 0..1, or null when the size is not known. */
        val fraction: Float? get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else null
    }

    /** The APK is downloaded, checked, and waits for the user to install it. */
    data class Ready(val release: ReleaseInfo, val file: File) : UpdateState

    data class Installing(val release: ReleaseInfo) : UpdateState

    data class Failed(
        val failure: UpdateFailure,
        val release: ReleaseInfo?,
        val detail: String? = null,
    ) : UpdateState
}

/** The update server could not be reached, or answered something unusable. */
class UpdateException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Reads and writes the server's release JSON. Tolerant: a bad entry is skipped, not fatal. */
object UpdateJson {
    fun parseRelease(body: String): ReleaseInfo? =
        runCatching { (JsonParser.parseString(body) as? JsonObject)?.let(::releaseFrom) }.getOrNull()

    fun parseReleases(body: String?): List<ReleaseInfo> {
        if (body.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = JsonParser.parseString(body)
            val array = when {
                root.isJsonArray -> root.asJsonArray
                root.isJsonObject -> root.asJsonObject.getAsJsonArray("releases")
                else -> null
            }
            array.orEmpty().mapNotNull { (it as? JsonObject)?.let(::releaseFrom) }
        }.getOrDefault(emptyList())
    }

    fun toJson(releases: List<ReleaseInfo>): String {
        val array = JsonArray()
        releases.forEach { release ->
            array.add(
                JsonObject().apply {
                    addProperty("tag", release.tag)
                    addProperty("versionName", release.versionName)
                    addProperty("versionCode", release.versionCode)
                    addProperty("publishedAt", release.publishedAt)
                    addProperty("changelog", release.changelog)
                    release.apk?.let { apk ->
                        add(
                            "apk",
                            JsonObject().apply {
                                addProperty("url", apk.url)
                                addProperty("size", apk.size)
                                addProperty("sha256", apk.sha256)
                            },
                        )
                    }
                },
            )
        }
        return JsonObject().apply { add("releases", array) }.toString()
    }

    private fun releaseFrom(obj: JsonObject): ReleaseInfo? {
        val tag = obj.string("tag") ?: return null
        val versionName = obj.string("versionName") ?: return null
        val versionCode = runCatching { obj.get("versionCode").asLong }.getOrNull()
            ?.takeIf { it > 0 } ?: return null
        val apk = (obj.get("apk") as? JsonObject)?.let { apkObject ->
            val url = apkObject.string("url") ?: return@let null
            ApkInfo(
                url = url,
                size = runCatching { apkObject.get("size").asLong }.getOrDefault(0L),
                sha256 = apkObject.string("sha256")?.lowercase(),
            )
        }
        return ReleaseInfo(
            tag = tag,
            versionName = versionName,
            versionCode = versionCode,
            publishedAt = obj.string("publishedAt"),
            changelog = obj.string("changelog").orEmpty(),
            apk = apk,
        )
    }

    private fun JsonObject.string(name: String): String? =
        runCatching { get(name).takeIf { !it.isJsonNull }?.asString }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun JsonArray?.orEmpty(): List<com.google.gson.JsonElement> = this?.toList() ?: emptyList()
}
