package com.vgallery.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vgallery.app.data.gallery.DeviceAlbum
import com.vgallery.app.data.gallery.groupAlbums
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Grouping similar folder names on home: "WhatsApp Images" and "WhatsApp Video" come out under one
 * heading, while a folder with no relatives stays on its own — the grid only changes where there
 * really is a family to gather, and never loses or duplicates a folder.
 */
@RunWith(AndroidJUnit4::class)
class AlbumGroupingTest {

    private fun album(name: String) = DeviceAlbum(
        name = name,
        count = 3,
        coverUri = Uri.parse("content://media/external/images/media/${name.hashCode()}"),
        sizeBytes = 1024L
    )

    private fun headings(albums: List<DeviceAlbum>): List<Pair<String, List<String>>> =
        groupAlbums(albums).filter { it.isFamily }.map { it.title to it.albums.map { a -> a.name } }

    @Test
    fun foldersSharingTheirFirstWordLandTogether() {
        val groups = headings(
            listOf(album("WhatsApp Images"), album("Camera"), album("WhatsApp Video"))
        )
        assertEquals(listOf("WhatsApp" to listOf("WhatsApp Images", "WhatsApp Video")), groups)
    }

    @Test
    fun everyFolderAppearsExactlyOnceAndOnlyFamiliesGetAHeading() {
        val albums = listOf(
            album("Camera"),
            album("Camera Roll"),
            album("Screenshots"),
            album("WhatsApp Images"),
            album("WhatsApp Video"),
            album("Downloads")
        )
        val groups = groupAlbums(albums)
        assertEquals(albums.sortedBy { it.name }, groups.flatMap { it.albums }.sortedBy { it.name })
        assertEquals(listOf("Camera", "WhatsApp"), groups.filter { it.isFamily }.map { it.title })
        assertEquals(
            listOf("Screenshots", "Downloads"),
            groups.filter { !it.isFamily }.map { it.albums.single().name }
        )
    }

    @Test
    fun pluralAndDigitsAreNotDifferentFamilies() {
        assertEquals(
            listOf("Download" to listOf("Download", "Downloads")),
            headings(listOf(album("Download"), album("Downloads")))
        )
        assertEquals(
            listOf("Camera" to listOf("Camera2", "Camera")),
            headings(listOf(album("Camera2"), album("Camera")))
        )
        assertEquals(
            listOf("Screenshots" to listOf("Screenshots (1)", "Screenshots (10)")),
            headings(listOf(album("Screenshots (1)"), album("Screenshots (10)")))
        )
        // DCIM camera folders differ only in the roll number.
        assertEquals(
            listOf("100ANDRO" to listOf("100ANDRO", "101ANDRO")),
            headings(listOf(album("100ANDRO"), album("101ANDRO")))
        )
    }

    @Test
    fun headingKeepsTheNameAsTheUserWroteIt() {
        val groups = headings(listOf(album("whatsapp video"), album("WhatsApp Images")))
        assertEquals(listOf("whatsapp"), groups.map { it.first })
        val other = headings(listOf(album("WhatsApp Images"), album("whatsapp video")))
        assertEquals(listOf("WhatsApp"), other.map { it.first })
    }

    @Test
    fun familiesComeInTheOrderTheirFirstFolderDoes() {
        val groups = headings(
            listOf(
                album("Camera"),
                album("WhatsApp Images"),
                album("WhatsApp Video"),
                album("Camera Roll")
            )
        )
        assertEquals(listOf("Camera", "WhatsApp"), groups.map { it.first })
        assertEquals(listOf("Camera", "Camera Roll"), groups[0].second)
    }

    @Test
    fun namesWithNothingInCommonStayAsTheyWere() {
        val albums = listOf(album("Pictures"), album("DCIM"), album("Bluetooth"))
        assertEquals(albums.map { it.name }, groupAlbums(albums).flatMap { it.albums }.map { it.name })
        assertTrue(groupAlbums(albums).none { it.isFamily })
    }

    @Test
    fun emptyAndUnnamedFoldersAreHandled() {
        assertTrue(groupAlbums(emptyList()).isEmpty())
        // A folder with no letters at all still gathers its numbered variants.
        assertEquals(
            listOf("2024" to listOf("2024", "2024 (1)")),
            headings(listOf(album("2024"), album("2024 (1)")))
        )
        // Non-Latin folder names group the same way.
        assertEquals(
            listOf("일부" to listOf("일부", "일부 2")),
            headings(listOf(album("일부"), album("일부 2")))
        )
    }

    @Test
    fun runTogetherNamesAreSplitIntoWords() {
        // "CameraRoll" is a camera folder, not a folder called "Cameraroll".
        assertEquals(
            listOf("Camera" to listOf("CameraRoll", "Camera Roll")),
            headings(listOf(album("CameraRoll"), album("Camera Roll")))
        )
        // A brand stays one word (its tail after the capital is short) and still meets its spaced
        // spelling, while the joined device folders meet each other.
        assertEquals(
            listOf("WhatsApp" to listOf("WhatsAppImages", "WhatsApp Video")),
            headings(listOf(album("WhatsAppImages"), album("WhatsApp Video")))
        )
        assertEquals(
            listOf("DCIM" to listOf("DCIM", "DCIM2")),
            headings(listOf(album("DCIM"), album("DCIM2")))
        )
    }

    @Test
    fun pluralsEndingInASibilantFoldToTheSingular() {
        assertEquals(
            listOf("Bus" to listOf("Buses", "Bus")),
            headings(listOf(album("Buses"), album("Bus")))
        )
        assertEquals(
            listOf("Watch" to listOf("Watches", "Watch")),
            headings(listOf(album("Watches"), album("Watch")))
        )
        // A plain "es" is not a plural ending: "Notes" belongs with "Note", not with "Not".
        assertEquals(
            listOf("Note" to listOf("Notes", "Note")),
            headings(listOf(album("Notes"), album("Note")))
        )
    }

    @Test
    fun accentsDoNotMakeADifferentFolder() {
        assertEquals(
            listOf("Cámara" to listOf("Cámara", "Camara")),
            headings(listOf(album("Cámara"), album("Camara")))
        )
    }
}
