package com.vgallery.app.data.update

/**
 * The version maths behind the in-app updater, deliberately free of Android and of the network so
 * it can be unit tested directly: parsing a release tag, deciding whether it is actually newer than
 * the running build, and picking the one asset that is worth downloading.
 */

/** Owner/repo of the project on GitHub, which hosts the release APKs. */
const val UPDATE_REPO = "realmjunaid/vgallery"

/** GitHub's "latest release" endpoint (drafts and pre-releases are excluded by GitHub itself). */
const val UPDATE_RELEASE_API = "https://api.github.com/repos/$UPDATE_REPO/releases/latest"

/** The GitLab mirror's releases, asked only when GitHub cannot answer. */
const val UPDATE_GITLAB_API = "https://gitlab.com/api/v4/projects/realmjunaid%2Fvgallery/releases"

/**
 * The mirror's tags. A tag is pushed to GitLab with every release even when no GitLab *release* is
 * created there, so this is what keeps the check working when GitHub's API is rate limited.
 */
const val UPDATE_GITLAB_TAGS_API =
    "https://gitlab.com/api/v4/projects/realmjunaid%2Fvgallery/repository/tags?per_page=30"

/** Downloads, the APK, and installs are refused on anything but HTTPS. */
const val UPDATE_HTTPS_PREFIX = "https://"

/** One downloadable asset of a release, as the host describes it. */
data class ReleaseAsset(
    val name: String,
    val url: String,
    /** The host's digest, e.g. `sha256:ab12…`; GitHub publishes it, older uploads may not. */
    val digest: String? = null,
)

/**
 * A published release. [apkUrl] is null when only the version is known (a GitLab-only release);
 * the download then falls back to the path this project's own build always produces.
 */
data class AppRelease(
    val versionName: String,
    val tag: String,
    val notes: String?,
    val apkUrl: String?,
    val apkSha256: String?,
    val source: String,
)

/**
 * `1.10.2` -> `[1, 10, 2]`; null when [raw] is not a version at all (a branch name, junk, empty).
 * A leading `v` is stripped and any pre-release suffix is ignored, so `v1.0.8-beta.2` reads as
 * `[1, 0, 8]` — a beta of a version is never treated as newer than the release itself. At least two
 * numeric parts are required, so a stray build tag like `2024` is not mistaken for a release.
 */
fun parseVersion(raw: String?): List<Int>? {
    val cleaned = raw?.trim()?.removePrefix("v")?.removePrefix("V")?.trim() ?: return null
    // Only the leading dotted number counts, so "1.0.8-beta.2" and "1.0.8+build.5" both read as 1.0.8.
    val numeric = cleaned.takeWhile { it.isDigit() || it == '.' }.trimEnd('.')
    if (numeric.isEmpty()) return null
    val parts = numeric.split('.').map { it.toIntOrNull() ?: return null }
    if (parts.size < 2) return null
    return parts
}

/** Numeric, part-by-part comparison; a missing part counts as 0, so `1.1` equals `1.1.0`. */
fun compareVersions(left: List<Int>, right: List<Int>): Int {
    for (index in 0 until maxOf(left.size, right.size)) {
        val a = left.getOrElse(index) { 0 }
        val b = right.getOrElse(index) { 0 }
        if (a != b) return if (a > b) 1 else -1
    }
    return 0
}

/** True when [latest] is strictly newer than the running [current] version. */
fun isNewerVersion(current: String?, latest: String?): Boolean {
    val currentParts = parseVersion(current) ?: return false
    val latestParts = parseVersion(latest) ?: return false
    return compareVersions(latestParts, currentParts) > 0
}

/**
 * The highest version among release tag names, so a mirror's tag list can stand in for its releases.
 * Unparseable tags (`latest`, a branch name, a year) are ignored rather than counted.
 */
fun newestVersionName(tags: List<String>): String? = tags
    .mapNotNull { tag ->
        val name = tag.trim().removePrefix("v").removePrefix("V")
        if (name.isEmpty() || parseVersion(name) == null) null else name
    }
    .maxWithOrNull { left, right -> compareVersions(parseVersion(left)!!, parseVersion(right)!!) }

/** The one asset worth downloading: the release APK first, any other APK after that. */
fun pickApkAsset(assets: List<ReleaseAsset>): ReleaseAsset? =
    assets.firstOrNull { it.name.endsWith("-release.apk", ignoreCase = true) }
        ?: assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

/** The bare lowercase hex of a host digest like `sha256:ab12…`; null for anything else. */
fun sha256Hex(digest: String?): String? {
    val value = digest?.trim()?.lowercase() ?: return null
    if (!value.startsWith("sha256:")) return null
    val hex = value.removePrefix("sha256:")
    return hex.takeIf { it.isNotEmpty() && it.all { c -> c in "0123456789abcdef" } }
}

/** The asset name this project's release build produces for [versionName]. */
fun releaseApkName(versionName: String): String = "vgallery_v$versionName-release.apk"

/** The tag a release for [versionName] is published under (`v1.0.8`). */
fun releaseTag(versionName: String): String = "v$versionName"

/** Only HTTPS downloads are accepted, so a tampered API reply can never downgrade the transport. */
fun isTrustedDownloadUrl(url: String?): Boolean {
    if (url == null || !url.startsWith(UPDATE_HTTPS_PREFIX)) return false
    return TRUSTED_HOSTS.any { host -> url.startsWith("$UPDATE_HTTPS_PREFIX$host/") }
}

/**
 * The download URL for a release whose assets we cannot enumerate (the GitLab fallback). GitHub is
 * still the host: the same tag is pushed to both remotes, and only GitHub carries the APK.
 */
fun githubApkUrl(tag: String, versionName: String): String =
    "$UPDATE_HTTPS_PREFIX$GITHUB_HOST/$UPDATE_REPO/releases/download/$tag/${releaseApkName(versionName)}"

/** GitHub itself, the object host its asset downloads redirect to, and the GitLab mirror. */
private val TRUSTED_HOSTS = listOf(GITHUB_HOST, "objects.githubusercontent.com", "gitlab.com")

private const val GITHUB_HOST = "github.com"
