package com.vgallery.app

import com.vgallery.app.ui.update.releaseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the update prompt is allowed to show.
 *
 * The dialog used to print the release note exactly as GitHub stores it: `**bold**` markers, the
 * "this build is big news" opening paragraph, and an eight-line cut that pushed the actual change
 * list behind an ellipsis. These tests pin the shape the user asked for — the list of changes, and
 * nothing else.
 */
class UpdateNotesTest {

    private val notes = """
        **In-app updates go live in this build.** Installed 1.0.7 apps will be offered 1.0.9 the next
        time they open, and from here on VGallery updates itself.

        **What's new**

        - **Updates are now reachable** - it downloads the APK and verifies its signature.
        - **Autoplay is a small icon** - one 32dp glyph with no badge behind it.
        - **Video countdown** - `0:12 / 0:29` while a clip plays.
    """.trimIndent()

    @Test
    fun onlyTheChangeListReachesTheDialog() {
        val shown = releaseNotes(notes).text

        assertTrue(shown.contains("\u2022 Updates are now reachable \u2014 it downloads"))
        assertTrue(shown.contains("\u2022 Autoplay is a small icon \u2014"))
        assertTrue(shown.contains("\u2022 Video countdown \u2014 0:12 / 0:29 while a clip plays."))
        assertEquals(3, shown.lines().size)
    }

    @Test
    fun markdownMarkersAndAnnouncementsAreGone() {
        val shown = releaseNotes(notes).text

        assertFalse(shown.contains("**"))
        assertFalse(shown.contains("`"))
        assertFalse(shown.contains("In-app updates go live"))
        assertFalse(shown.contains("What's new"))
    }

    @Test
    fun aNoteWithoutAListIsStillShownCleaned() {
        val shown = releaseNotes("**A paragraph** - about the release, with no list at all.").text

        assertEquals("A paragraph \u2014 about the release, with no list at all.", shown)
    }

    @Test
    fun headingsBecomePlainLinesAndNumbersStayBullets() {
        val shown = releaseNotes("### Notes\n1. First thing\n2. Second thing").text

        assertEquals("\u2022 First thing\n\u2022 Second thing", shown)
    }
}
