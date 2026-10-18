package com.vgallery.app

import com.vgallery.app.data.update.AppRelease
import com.vgallery.app.data.update.ReleaseAsset
import com.vgallery.app.data.update.UpdateState
import com.vgallery.app.data.update.compareVersions
import com.vgallery.app.data.update.githubApkUrl
import com.vgallery.app.data.update.isNewerVersion
import com.vgallery.app.data.update.isTrustedDownloadUrl
import com.vgallery.app.data.update.newestVersionName
import com.vgallery.app.data.update.parseVersion
import com.vgallery.app.data.update.pickApkAsset
import com.vgallery.app.data.update.releaseApkName
import com.vgallery.app.data.update.sha256Hex
import com.vgallery.app.ui.update.updateStateSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The updater's decision-making, which is the part that must never be wrong: what counts as a
 * version, when a tag means "there is an update", which asset to download, and which hosts and
 * digests are trusted. All pure logic, so it is tested without a device in the loop.
 */
class UpdateVersionTest {

    // ── Parsing and comparing ─────────────────────────────────────────────────────────────

    @Test
    fun tagsParseWithOrWithoutTheVPrefix() {
        assertEquals(listOf(1, 0, 7), parseVersion("1.0.7"))
        assertEquals(listOf(1, 0, 8), parseVersion("v1.0.8"))
        assertEquals(listOf(1, 0, 8), parseVersion(" V1.0.8 "))
        assertEquals(listOf(2, 10), parseVersion("2.10"))
    }

    @Test
    fun aPreReleaseSuffixIsIgnored() {
        assertEquals(listOf(1, 0, 8), parseVersion("1.0.8-beta.2"))
        assertEquals(listOf(1, 0, 8), parseVersion("v1.0.8+build.5"))
    }

    @Test
    fun nonVersionsAreRejected() {
        assertNull(parseVersion(null))
        assertNull(parseVersion(""))
        assertNull(parseVersion("latest"))
        assertNull(parseVersion("main"))
        assertNull(parseVersion("screenshot-2024.10.18"))
        // A single number (a stray build tag) must not look like a release.
        assertNull(parseVersion("2024"))
    }

    @Test
    fun versionsCompareNumericallyNotLexically() {
        assertTrue(compareVersions(listOf(1, 10, 0), listOf(1, 9, 9)) > 0)
        assertTrue(compareVersions(listOf(1, 9, 9), listOf(1, 10, 0)) < 0)
        // A missing part counts as zero, so 1.1 and 1.1.0 are the same release.
        assertEquals(0, compareVersions(listOf(1, 1), listOf(1, 1, 0)))
    }

    @Test
    fun onlyAStrictlyNewerTagIsAnUpdate() {
        assertTrue(isNewerVersion("1.0.7", "1.0.8"))
        assertTrue(isNewerVersion("1.0.7", "v1.1.0"))
        assertTrue(isNewerVersion("1.9.9", "1.10.0"))
        assertFalse(isNewerVersion("1.0.7", "1.0.7"))
        assertFalse(isNewerVersion("1.0.7", "1.0.6"))
        assertFalse(isNewerVersion("1.0.7", null))
        assertFalse(isNewerVersion(null, "1.0.8"))
        assertFalse(isNewerVersion("1.0.7", "not-a-version"))
    }

    // ── Assets, digests and URLs ──────────────────────────────────────────────────────────

    @Test
    fun theReleaseApkIsTheAssetChosen() {
        val assets = listOf(
            ReleaseAsset("SHA256SUMS.txt", "https://github.com/x/SHA256SUMS.txt"),
            ReleaseAsset("vgallery_v1.0.8.apk", "https://github.com/x/debug.apk"),
            ReleaseAsset("vgallery_v1.0.8-release.apk", "https://github.com/x/release.apk"),
        )
        assertEquals("https://github.com/x/release.apk", pickApkAsset(assets)?.url)

        val onlyDebug = listOf(
            ReleaseAsset("notes.md", "https://github.com/x/notes.md"),
            ReleaseAsset("vgallery_v1.0.8.apk", "https://github.com/x/debug.apk"),
        )
        assertEquals("https://github.com/x/debug.apk", pickApkAsset(onlyDebug)?.url)
        assertNull(pickApkAsset(emptyList()))
    }

    @Test
    fun onlyASha256DigestIsUsable() {
        assertEquals("ab12cd", sha256Hex("sha256:AB12CD"))
        assertEquals("ab12cd", sha256Hex("sha256:ab12cd"))
        assertNull(sha256Hex("sha256:"))
        assertNull(sha256Hex("md5:ab12cd"))
        assertNull(sha256Hex(null))
        // Guards against a digest that is really a sentence.
        assertNull(sha256Hex("sha256:not a hash"))
    }

    @Test
    fun theFallbackDownloadUrlMatchesThisProjectsApkName() {
        assertEquals("vgallery_v1.0.8-release.apk", releaseApkName("1.0.8"))
        assertEquals(
            "https://github.com/realmjunaid/vgallery/releases/download/v1.0.8/" +
                "vgallery_v1.0.8-release.apk",
            githubApkUrl("v1.0.8", "1.0.8")
        )
    }

    @Test
    fun downloadsAreOnlyEverTakenOverHttpsFromOurHosts() {
        assertTrue(isTrustedDownloadUrl("https://github.com/realmjunaid/vgallery/releases/x.apk"))
        assertTrue(isTrustedDownloadUrl("https://objects.githubusercontent.com/x.apk"))
        assertTrue(isTrustedDownloadUrl("https://gitlab.com/realmjunaid/vgallery/x.apk"))
        assertFalse(isTrustedDownloadUrl("http://github.com/realmjunaid/vgallery/x.apk"))
        assertFalse(isTrustedDownloadUrl("https://example.com/vgallery.apk"))
        assertFalse(isTrustedDownloadUrl("https://github.com.evil.test/vgallery.apk"))
        assertFalse(isTrustedDownloadUrl(null))
    }

    // ── What the Settings row says ────────────────────────────────────────────────────────

    @Test
    fun everyUpdaterStepReadsWellInTheSettingsRow() {
        val release = AppRelease(
            versionName = "1.0.8",
            tag = "v1.0.8",
            notes = null,
            apkUrl = "https://github.com/x/y.apk",
            apkSha256 = null,
            source = "GitHub",
        )
        assertEquals("Version 1.0.7", updateStateSummary(UpdateState.Idle, "1.0.7"))
        assertEquals("Checking for updates\u2026", updateStateSummary(UpdateState.Checking, "1.0.7"))
        assertEquals("Up to date", updateStateSummary(UpdateState.UpToDate("1.0.7"), "1.0.7"))
        assertEquals(
            "Version 1.0.8 available",
            updateStateSummary(UpdateState.Available(release, manual = false), "1.0.7")
        )
        assertEquals(
            "Downloading\u2026 40%",
            updateStateSummary(UpdateState.Downloading(release, 0.4f), "1.0.7")
        )
        assertEquals(
            "Downloading\u2026",
            updateStateSummary(UpdateState.Downloading(release, -1f), "1.0.7")
        )
        assertEquals(
            "Ready to install",
            updateStateSummary(UpdateState.ReadyToInstall(release, "/tmp/x.apk"), "1.0.7")
        )
        assertEquals("Check failed", updateStateSummary(UpdateState.Failed("nope"), "1.0.7"))
        // A failed *download* must not be reported as a failed check: the download is the step the
        // user just watched fail.
        assertEquals(
            "Update failed",
            updateStateSummary(
                UpdateState.Failed("nope", UpdateState.Failed.Phase.DOWNLOAD),
                "1.0.7"
            )
        )
    }

    @Test
    fun theNewestTagWinsAndJunkIsIgnored() {
        assertEquals("1.0.9", newestVersionName(listOf("v1.0.7", "v1.0.9", "v1.0.8")))
        assertEquals("1.0.10", newestVersionName(listOf("v1.0.9", "v1.0.10")))
        assertEquals("2.0.0", newestVersionName(listOf("2.0.0", "v1.9.9")))
        // Nothing version-like: a branch name, a year, an empty list.
        assertNull(newestVersionName(listOf("latest", "2024", "")))
        assertNull(newestVersionName(emptyList()))
    }
}
