package com.tulip.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private fun entry(
    id: Long, name: String = "f$id.mp4", path: String = "Movies/", size: Long = id, mod: Long = id,
    taken: Long? = null, kind: MediaKind = MediaKind.VIDEO, volume: String = "external_primary",
) = MediaEntry(id, "content://x/$id", name, path, volume, "video/mp4", size, id, mod, taken, 0, 0, 0, kind)

class SortingTest {
    private val l = listOf(entry(1, "b.mp4", size = 30, mod = 5), entry(2, "A.mp4", size = 10, mod = 9), entry(3, "c.mp4", size = 20, mod = 1))

    @Test fun byNameIgnoringCase() = assertEquals(listOf(2L, 1L, 3L), Sorting.sort(l, SortOrder(SortField.NAME, true)).map { it.id })
    @Test fun bySizeDescending() = assertEquals(listOf(1L, 3L, 2L), Sorting.sort(l, SortOrder(SortField.SIZE, false)).map { it.id })
    @Test fun byModifiedAscending() = assertEquals(listOf(3L, 1L, 2L), Sorting.sort(l, SortOrder(SortField.MODIFIED, true)).map { it.id })

    @Test fun takenNeverInventsDatesAndPutsMissingLast() {
        val t = listOf(entry(1, taken = null), entry(2, taken = 50), entry(3, taken = 10))
        assertEquals(listOf(3L, 2L, 1L), Sorting.sort(t, SortOrder(SortField.TAKEN, true)).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), Sorting.sort(t, SortOrder(SortField.TAKEN, false)).map { it.id })
    }

    @Test fun sortOrderEncodeDecode() {
        val o = SortOrder(SortField.SIZE, false)
        assertEquals(o, SortOrder.decode(o.encode()))
        assertEquals(SortOrder.Default, SortOrder.decode("basura"))
        assertEquals(SortOrder.Default, SortOrder.decode(null))
    }

    @Test fun filterByQueryNameAndPath() {
        val e = listOf(entry(1, "gato.mp4", "Movies/Gatitos/"), entry(2, "perro.mp4", "Movies/Otros/"))
        assertEquals(listOf(1L), MediaFilter(query = "gato").apply(e).map { it.id })
        assertEquals(listOf(2L), MediaFilter(query = "otros/").apply(e).map { it.id })
        assertEquals(listOf(2L), MediaFilter(extensions = setOf("mp4"), folderContains = "otros").apply(e).map { it.id })
    }
}

class ExclusionsTest {
    @Test fun excludedFolderAndSubfolders() {
        val ex = setOf(FolderExclusions.key("external_primary", "Movies/Clases"))
        assertTrue(FolderExclusions.isExcluded("external_primary", "Movies/Clases/", ex))
        assertTrue(FolderExclusions.isExcluded("external_primary", "Movies/Clases/Tema1/", ex))
        assertFalse(FolderExclusions.isExcluded("external_primary", "Movies/Clases2/", ex))
    }

    @Test fun sameNameDifferentLocationIsNotExcluded() {
        val ex = setOf(FolderExclusions.key("external_primary", "Movies/Clases/"))
        assertFalse(FolderExclusions.isExcluded("external_primary", "DCIM/Clases/", ex))
        assertFalse(FolderExclusions.isExcluded("1234-ABCD", "Movies/Clases/", ex))
    }
}

class ShuffleTest {
    @Test fun queueHasNoRepeatsAndSkipsExcluded() {
        val vids = (1L..50L).map { entry(it, path = if (it % 5 == 0L) "Movies/Clases/" else "Movies/Otros/") } +
            entry(99, kind = MediaKind.IMAGE)
        val ex = setOf(FolderExclusions.key("external_primary", "Movies/Clases/"))
        val q = Shuffler.buildVideoQueue(vids, ex, Random(7))
        assertEquals(q.map { it.id }.toSet().size, q.size)
        assertEquals(40, q.size)
        assertTrue(q.none { it.relativePath.contains("Clases") || it.kind != MediaKind.VIDEO })
    }

    @Test fun queueIsCapped() = assertEquals(10, Shuffler.buildVideoQueue((1L..100L).map { entry(it) }, emptySet(), Random(1), max = 10).size)

    @Test fun newCycleDoesNotStartWithPreviousLast() {
        repeat(200) { seed ->
            val r = Shuffler.shuffleAvoidingRepeat((1..5).toList(), Random(seed), previousLast = 3)
            assertTrue(r.first() != 3)
            assertEquals(setOf(1, 2, 3, 4, 5), r.toSet())
        }
    }

    @Test fun queueStateRoundTrip() {
        val s = QueueState(listOf("content://a/1", "content://a/2"), 1, 4500)
        assertEquals(s, QueueState.decode(s.encode()))
        assertNull(QueueState.decode(""))
        assertNull(QueueState.decode("x|y\nabc"))
        assertEquals(1, QueueState.decode("9|0\nu1\nu2")!!.index)
    }
}

class FolderAndNameTest {
    @Test fun browseSplitsFoldersAndItems() {
        val e = listOf(entry(1, path = "DCIM/Camera/"), entry(2, path = "DCIM/Camera/"), entry(3, path = "DCIM/Tulip/"), entry(4, path = "DCIM/"))
        val root = FolderTree.browse(e, "")
        assertEquals(listOf("DCIM"), root.folders.map { it.name })
        val dcim = FolderTree.browse(e, "DCIM/")
        assertEquals(listOf("Camera", "Tulip"), dcim.folders.map { it.name })
        assertEquals(2, dcim.folders.first().count)
        assertEquals(listOf(4L), dcim.items.map { it.id })
    }

    @Test fun sanitizeAndUnique() {
        assertEquals("foto.jpg", NameRules.sanitizeFileName("foto", "x.jpg"))
        assertEquals("foto.png", NameRules.sanitizeFileName("foto.png", "x.jpg"))
        assertNull(NameRules.sanitizeFileName("a/b", "x.jpg"))
        assertNull(NameRules.sanitizeFolderName("../x"))
        assertEquals("Gatitos", NameRules.sanitizeFolderName(" Gatitos "))
        val taken = setOf("a.mp4", "a (1).mp4")
        assertEquals("a (2).mp4", NameRules.uniqueName("a.mp4") { it in taken })
    }

    @Test fun classify() {
        assertEquals(MediaKind.ARCHIVE, Classifier.classify("application/octet-stream", "x.zip"))
        assertEquals(MediaKind.DOCUMENT, Classifier.classify("application/pdf", "x.pdf"))
        assertEquals(MediaKind.VIDEO, Classifier.classify("video/mp4", "x.mp4"))
        assertEquals(MediaKind.OTHER, Classifier.classify(null, "x.bin"))
    }
}

class PhotoFilterTest {
    @Test fun defaultsAreIdentity() {
        val m = PhotoFilterMath.build(FilterPreset.NONE, 0f, 1f, 1f)
        for (i in 0 until 20) assertEquals(PhotoFilterMath.identity[i], m[i], 1e-4f)
    }

    @Test fun blackAndWhiteMakesChannelsEqual() {
        val m = PhotoFilterMath.preset(FilterPreset.BW)
        fun apply(r: Float, g: Float, b: Float) = FloatArray(3) { c -> m[c * 5] * r + m[c * 5 + 1] * g + m[c * 5 + 2] * b + m[c * 5 + 4] }
        val o = apply(200f, 40f, 90f)
        assertEquals(o[0], o[1], 1e-2f); assertEquals(o[1], o[2], 1e-2f)
    }

    @Test fun brightnessShiftsAndThenComposes() {
        val b = PhotoFilterMath.contrastBrightness(1f, 0.1f)
        assertEquals(25.5f, b[4], 1e-3f)
        val c = PhotoFilterMath.then(PhotoFilterMath.identity, b)
        assertEquals(25.5f, c[4], 1e-3f)
    }
}
