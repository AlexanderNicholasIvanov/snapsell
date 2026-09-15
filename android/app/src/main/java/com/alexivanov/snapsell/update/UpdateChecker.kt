package com.alexivanov.snapsell.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** A newer build published on GitHub Releases. The user downloads it; nothing is automatic. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseUrl: String,
    val sizeBytes: Long,
    val notes: String,
)

/** Where to look. Values come from BuildConfig (UPDATE_REPO / UPDATE_TOKEN / UPDATE_MANIFEST_URL). */
data class UpdateConfig(
    val repo: String,
    val token: String = "",
    /** When non-empty this URL replaces the GitHub API URL entirely (used for testing against a local file). */
    val manifestUrl: String = "",
) {
    val url: String get() = manifestUrl.ifBlank { "https://api.github.com/repos/$repo/releases/latest" }
}

/** The subset of GitHub's release JSON that matters. */
@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("name") val name: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("assets") val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
internal data class GitHubAsset(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @SerialName("size") val size: Long = 0,
)

/**
 * GETs the latest release and decides whether it is newer than the running
 * build. Every failure (network, 404 on a private repo without a token,
 * malformed JSON, no APK asset) is a quiet null: the check must never surface
 * an error or block the UI.
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val config: UpdateConfig,
    private val log: (String) -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** What a check found. Only Settings' manual check cares about the difference between the last two. */
    sealed interface Outcome {
        data class Newer(val info: UpdateInfo) : Outcome
        /** The request worked; there is nothing newer (or the release is unusable, e.g. no APK asset). */
        data object UpToDate : Outcome
        data object Failed : Outcome
    }

    suspend fun check(currentVersionCode: Int): UpdateInfo? = (checkDetailed(currentVersionCode) as? Outcome.Newer)?.info

    suspend fun checkDetailed(currentVersionCode: Int): Outcome = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(config.url)
                .header("Accept", "application/vnd.github+json")
                .apply { if (config.token.isNotBlank()) header("Authorization", "Bearer ${config.token}") }
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    log("update check: HTTP ${response.code} from ${config.url}")
                    return@withContext Outcome.Failed
                }
                parseDetailed(response.body.string(), currentVersionCode)
            }
        } catch (e: Exception) {
            log("update check failed: ${e.message ?: e::class.java.simpleName}")
            Outcome.Failed
        }
    }

    /** Pure decision logic, exposed for tests. */
    fun parse(releaseJson: String, currentVersionCode: Int): UpdateInfo? =
        (parseDetailed(releaseJson, currentVersionCode) as? Outcome.Newer)?.info

    fun parseDetailed(releaseJson: String, currentVersionCode: Int): Outcome {
        val release = try {
            json.decodeFromString(GitHubRelease.serializer(), releaseJson)
        } catch (e: Exception) {
            log("update check: bad JSON: ${e.message}")
            return Outcome.Failed
        }
        val versionCode = versionCodeOf(release)
        if (versionCode == null) {
            log("update check: no version code in body or tag '${release.tagName}'")
            return Outcome.Failed
        }
        if (versionCode <= currentVersionCode) {
            log("update check: ${release.tagName} (code $versionCode) is not newer than $currentVersionCode")
            return Outcome.UpToDate
        }
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.isNotBlank() }
        if (apk == null) {
            log("update check: release ${release.tagName} has no .apk asset")
            return Outcome.UpToDate
        }
        return Outcome.Newer(
            UpdateInfo(
                versionCode = versionCode,
                versionName = release.tagName,
                apkUrl = apk.browserDownloadUrl,
                releaseUrl = release.htmlUrl,
                sizeBytes = apk.size,
                notes = release.body.orEmpty(),
            ),
        )
    }

    companion object {
        private val BODY_VERSION = Regex("""version_code:\s*(\d+)""")
        private val TAG_LAST_NUMBER = Regex("""(\d+)\D*$""")

        /** `version_code: N` in the release body wins; else the last numeric segment of the tag ("v0.1.42" -> 42). */
        internal fun versionCodeOf(release: GitHubRelease): Int? =
            release.body?.let { BODY_VERSION.find(it)?.groupValues?.get(1)?.toIntOrNull() }
                ?: TAG_LAST_NUMBER.find(release.tagName)?.groupValues?.get(1)?.toIntOrNull()

        /** "21 MB" style, for the banner. */
        fun formatSize(bytes: Long): String = when {
            bytes <= 0 -> ""
            bytes < 1_000_000 -> "${(bytes / 1_000.0).toInt().coerceAtLeast(1)} KB"
            else -> "${Math.round(bytes / 1_000_000.0)} MB"
        }
    }
}
