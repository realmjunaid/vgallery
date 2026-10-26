package com.vgallery.app.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the newest published release.
 *
 * GitHub is the primary source because it is the one that actually hosts the APK assets. The GitLab
 * mirror (same tags, pushed together) is asked only when GitHub cannot answer — and then just the
 * version is trusted, since the APK is still fetched from GitHub.
 *
 * Everything here is best-effort: no network, a proxy, a rate limit or a malformed reply all end in
 * `null`, which the UI reports as "could not check" and never as a crash.
 */
@Singleton
class UpdateChecker @Inject constructor() {

    /** The newest release, or null when no host could be reached or understood. */
    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        fetchGitHub() ?: fetchGitLabRelease() ?: fetchGitLabTag()
    }

    private fun fetchGitHub(): AppRelease? {
        val body = try {
            read(UPDATE_RELEASE_API, "application/vnd.github+json")
        } catch (_: Exception) {
            return null
        }
        return try {
            val json = JSONObject(body)
            val tag = json.optString("tag_name").trim()
            val version = tag.removePrefix("v")
            if (tag.isEmpty() || parseVersion(version) == null) return null
            val canonicalTag = if (tag.startsWith("v")) tag else releaseTag(version)
            val assets = json.optJSONArray("assets").toAssetList()
            val apk = pickApkAsset(assets)
            AppRelease(
                versionName = version,
                tag = canonicalTag,
                notes = json.optString("body").takeIf { it.isNotBlank() }?.let { clamp(it) },
                apkUrl = apk?.url?.takeIf(::isTrustedDownloadUrl)
                    ?: githubApkUrl(canonicalTag, version),
                apkSha256 = sha256Hex(apk?.digest),
                source = "GitHub",
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchGitLabRelease(): AppRelease? {
        val body = try {
            read(UPDATE_GITLAB_API, "application/json")
        } catch (_: Exception) {
            return null
        }
        return try {
            val releases = JSONArray(body)
            if (releases.length() == 0) return null
            val release = releases.optJSONObject(0) ?: return null
            val tag = release.optString("tag_name").trim()
            val version = tag.removePrefix("v")
            if (tag.isEmpty() || parseVersion(version) == null) return null
            val canonicalTag = if (tag.startsWith("v")) tag else releaseTag(version)
            val apk = pickApkAsset(release.optJSONObject("assets")?.optJSONArray("links").toAssetList())
            AppRelease(
                versionName = version,
                tag = canonicalTag,
                notes = release.optString("description").takeIf { it.isNotBlank() }?.let { clamp(it) },
                apkUrl = apk?.url?.takeIf(::isTrustedDownloadUrl)
                    ?: githubApkUrl(canonicalTag, version),
                apkSha256 = null,
                source = "GitLab",
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * The last resort: the mirror's tags, which are pushed with every release even when no GitLab
     * release object exists there. Only the version is read — the APK still comes from GitHub, under
     * the same tag — and this is what keeps a check working when GitHub's API is rate limited.
     */
    private fun fetchGitLabTag(): AppRelease? {
        val body = try {
            read(UPDATE_GITLAB_TAGS_API, "application/json")
        } catch (_: Exception) {
            return null
        }
        return try {
            val tags = JSONArray(body)
            val names = (0 until tags.length()).mapNotNull { index ->
                tags.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }
            }
            val version = newestVersionName(names) ?: return null
            val tag = releaseTag(version)
            AppRelease(
                versionName = version,
                tag = tag,
                notes = null,
                apkUrl = githubApkUrl(tag, version),
                apkSha256 = null,
                source = "GitLab tag",
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Normalises GitHub (`browser_download_url`, `digest`) and GitLab (`direct_asset_url`) assets. */
    private fun JSONArray?.toAssetList(): List<ReleaseAsset> {
        if (this == null) return emptyList()
        val assets = mutableListOf<ReleaseAsset>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val url = item.optString("browser_download_url").takeIf { it.isNotBlank() }
                ?: item.optString("direct_asset_url").takeIf { it.isNotBlank() }
            if (name.isEmpty() || url == null) continue
            assets += ReleaseAsset(
                name = name,
                url = url,
                digest = item.optString("digest").takeIf { it.isNotBlank() },
            )
        }
        return assets
    }

    /** A release body is only ever shown in a dialog, so it is clamped rather than trusted. */
    private fun clamp(text: String): String =
        if (text.length > MAX_NOTES_CHARS) text.take(MAX_NOTES_CHARS) + "…" else text

    private fun read(url: String, accept: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code from ${connection.url?.host}")
            val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
            reader.use {
                val builder = StringBuilder()
                val buffer = CharArray(8 * 1024)
                while (true) {
                    val read = it.read(buffer)
                    if (read < 0) break
                    builder.append(buffer, 0, read)
                    if (builder.length > MAX_RESPONSE_CHARS) throw IOException("Reply too large")
                }
                return builder.toString()
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
        const val MAX_RESPONSE_CHARS = 256 * 1024
        const val MAX_NOTES_CHARS = 1_500
        const val USER_AGENT = "VGallery-Android"
    }
}
