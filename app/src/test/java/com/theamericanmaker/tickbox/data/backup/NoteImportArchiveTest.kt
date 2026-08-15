// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox.data.backup

import com.theamericanmaker.tickbox.data.model.ChecklistIconStyle
import com.theamericanmaker.tickbox.data.model.NoteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Ported from Smart Toolkit's test of the same parser, which is how existing archives
 * stay importable. Runs under Robolectric so `org.json` is the real Android
 * implementation — the same code production parses with — rather than a stub or a
 * differently-licensed substitute.
 */
@RunWith(RobolectricTestRunner::class)
class NoteImportArchiveTest {

    @Test
    fun validArchiveStagesSuccessfully() {
        val archiveBytes = zipBytes(
            "notes.json" to """
                {
                  "version": 1,
                  "notes": [
                    {
                      "title": "Trip",
                      "content": "Pack charger",
                      "type": "TEXT",
                      "isPinned": true,
                      "iconStyle": "STAR",
                      "createdAt": 111,
                      "updatedAt": 222,
                      "images": [
                        { "filename": "photo.jpg", "position": 0 }
                      ]
                    }
                  ]
                }
            """.trimIndent().toByteArray(),
            "images/photo.jpg" to byteArrayOf(1, 2, 3, 4),
        )

        withTempDir { stagingDir ->
            val archive = NoteImportArchive.stageValidatedArchive(archiveBytes.inputStream(), stagingDir)

            assertEquals(1, archive.notes.size)
            val note = archive.notes.first().note
            assertEquals("Trip", note.title)
            assertEquals(NoteType.TEXT, note.type)
            assertTrue(note.isPinned)
            assertEquals(ChecklistIconStyle.STAR, note.iconStyle)
            assertEquals(111, note.createdAt)
            assertEquals(222, note.updatedAt)
            assertEquals("photo.jpg", archive.notes.first().images.first().archiveName)
            assertTrue(archive.stagedImages.getValue("photo.jpg").stagedFile.exists())
        }
    }

    @Test
    fun checklistItemsParseWithIndentAndCheckedState() {
        val archiveBytes = zipBytes(
            "notes.json" to """
                {
                  "notes": [
                    {
                      "title": "List",
                      "type": "CHECKLIST",
                      "checklistItems": [
                        { "text": "Milk", "isChecked": true, "position": 0, "indentLevel": 0 },
                        { "text": "Whole", "isChecked": false, "position": 1, "indentLevel": 1 }
                      ]
                    }
                  ]
                }
            """.trimIndent().toByteArray(),
        )

        withTempDir { stagingDir ->
            val archive = NoteImportArchive.stageValidatedArchive(archiveBytes.inputStream(), stagingDir)
            val items = archive.notes.single().note.checklistItems
            assertEquals(listOf("Milk", "Whole"), items.map { it.text })
            assertEquals(listOf(true, false), items.map { it.isChecked })
            assertEquals(listOf(0, 1), items.map { it.indentLevel })
        }
    }

    // A Smart Toolkit archive never contains colorLabel — its exporter omitted the
    // field. Absent and explicit-null must both land on null, and a real value must
    // survive: this trio is the compatibility contract for the colour-label fix.

    @Test
    fun absentColorLabelBecomesNull() {
        val archive = parseSingleNote("""{ "notes": [ { "title": "t", "type": "TEXT" } ] }""")
        assertNull(archive.notes.single().note.colorLabel)
    }

    @Test
    fun explicitNullColorLabelBecomesNull() {
        val archive = parseSingleNote(
            """{ "notes": [ { "title": "t", "type": "TEXT", "colorLabel": null } ] }""",
        )
        assertNull(archive.notes.single().note.colorLabel)
    }

    @Test
    fun presentColorLabelSurvives() {
        val archive = parseSingleNote(
            """{ "notes": [ { "title": "t", "type": "TEXT", "colorLabel": "Red" } ] }""",
        )
        assertEquals("Red", archive.notes.single().note.colorLabel)
    }

    @Test
    fun archivesFromANewerVersionAreRefusedWithAClearMessage() {
        val error = assertThrows(ImportValidationException::class.java) {
            parseSingleNote("""{ "version": ${CURRENT_ARCHIVE_VERSION + 1}, "notes": [] }""")
        }
        assertEquals("This backup was made by a newer version of Tickbox.", error.message)
    }

    @Test
    fun missingVersionIsTreatedAsVersionOne() {
        val archive = parseSingleNote("""{ "notes": [ { "title": "legacy", "type": "TEXT" } ] }""")
        assertEquals("legacy", archive.notes.single().note.title)
    }

    @Test
    fun unknownTypeAndIconStyleFallBackToDefaults() {
        val archive = parseSingleNote(
            """{ "notes": [ { "title": "t", "type": "BOGUS", "iconStyle": "SPARKLE" } ] }""",
        )
        assertEquals(NoteType.TEXT, archive.notes.single().note.type)
        assertEquals(ChecklistIconStyle.CHECKBOX, archive.notes.single().note.iconStyle)
    }

    @Test
    fun rejectsTraversalPaths() {
        assertRejected(
            "Backup contains invalid file paths.",
            "notes.json" to """{ "notes": [] }""".toByteArray(),
            "images/../evil.jpg" to byteArrayOf(1),
        )
    }

    @Test
    fun rejectsAbsolutePaths() {
        assertRejected(
            "Backup contains invalid file paths.",
            "/notes.json" to """{ "notes": [] }""".toByteArray(),
        )
    }

    @Test
    fun rejectsBackslashPaths() {
        assertRejected(
            "Backup contains invalid file paths.",
            "notes.json" to """{ "notes": [] }""".toByteArray(),
            "images\\evil.jpg" to byteArrayOf(1),
        )
    }

    @Test
    fun rejectsUnexpectedTopLevelFiles() {
        assertRejected(
            "Backup contains unsupported files.",
            "notes.json" to """{ "notes": [] }""".toByteArray(),
            "evil.txt" to "nope".toByteArray(),
        )
    }

    @Test
    fun rejectsNestedImagePaths() {
        assertRejected(
            "Backup contains invalid image paths.",
            "notes.json" to """{ "notes": [] }""".toByteArray(),
            "images/sub/photo.jpg" to byteArrayOf(1),
        )
    }

    @Test
    fun rejectsUnsafeImageNames() {
        assertRejected(
            "Backup contains invalid image names.",
            "notes.json" to """{ "notes": [] }""".toByteArray(),
            "images/we ird.jpg" to byteArrayOf(1),
        )
    }

    @Test
    fun rejectsDirectoryEntries() {
        assertRejected(
            "Backup contains unsupported folders.",
            "notes.json" to """{ "notes": [] }""".toByteArray(),
            "images/" to ByteArray(0),
        )
    }

    @Test
    fun rejectsDuplicateNotesJsonEntries() {
        val archiveBytes = duplicateNotesArchive()
        withTempDir { stagingDir ->
            val error = assertThrows(ImportValidationException::class.java) {
                NoteImportArchive.stageValidatedArchive(archiveBytes.inputStream(), stagingDir)
            }
            assertEquals("Backup contains duplicate notes data.", error.message)
        }
    }

    @Test
    fun rejectsMissingNotesJson() {
        assertRejected(
            "Backup is missing notes.json.",
            "images/photo.jpg" to byteArrayOf(1, 2, 3),
        )
    }

    @Test
    fun rejectsMissingReferencedImages() {
        assertRejected(
            "Backup is missing an attached image.",
            "notes.json" to """
                {
                  "notes": [
                    { "title": "Trip", "type": "TEXT",
                      "images": [ { "filename": "missing.jpg", "position": 0 } ] }
                  ]
                }
            """.trimIndent().toByteArray(),
        )
    }

    @Test
    fun rejectsTooManyImagesOnOneNote() {
        val refs = (0..5).joinToString(",") { """{ "filename": "i$it.jpg", "position": $it }""" }
        val entries = mutableListOf<Pair<String, ByteArray>>(
            "notes.json" to """{ "notes": [ { "title": "t", "images": [$refs] } ] }""".toByteArray(),
        )
        (0..5).forEach { entries += "images/i$it.jpg" to byteArrayOf(it.toByte()) }
        assertRejected("Backup note has too many images.", *entries.toTypedArray())
    }

    @Test
    fun rejectsOversizedNotesJson() {
        val oversizedJson = buildString {
            append("{ \"notes\": [ { \"title\": \"A\", \"content\": \"")
            append("x".repeat((MAX_IMPORT_NOTES_JSON_BYTES + 1).toInt()))
            append("\" } ] }")
        }.toByteArray()
        assertRejected("Backup notes data is too large.", "notes.json" to oversizedJson)
    }

    @Test
    fun rejectsOversizedImageEntries() {
        assertRejected(
            "Backup image is too large.",
            "notes.json" to """{ "notes": [] }""".toByteArray(),
            "images/photo.jpg" to ByteArray(MAX_IMPORT_IMAGE_BYTES.toInt() + 1) { 7 },
        )
    }

    @Test
    fun rejectsTooManyImagesOverall() {
        val entries = mutableListOf<Pair<String, ByteArray>>(
            "notes.json" to """{ "notes": [] }""".toByteArray(),
        )
        repeat(MAX_IMPORT_IMAGE_COUNT + 1) { index ->
            entries += "images/image_$index.jpg" to byteArrayOf(index.toByte())
        }
        assertRejected("Backup contains too many images.", *entries.toTypedArray())
    }

    @Test
    fun generatesSafeImportedImageNames() {
        val generated = NoteImportArchive.generateImportedFileName("photo.JPG")
        assertTrue(generated.endsWith(".jpg"))
        assertNotEquals("photo.JPG", generated)

        assertTrue(NoteImportArchive.generateImportedFileName("no_extension").endsWith(".bin"))
    }

    private fun parseSingleNote(json: String): ValidatedNoteImportArchive {
        val archiveBytes = zipBytes("notes.json" to json.toByteArray())
        var result: ValidatedNoteImportArchive? = null
        withTempDir { stagingDir ->
            result = NoteImportArchive.stageValidatedArchive(archiveBytes.inputStream(), stagingDir)
        }
        return result!!
    }

    private fun assertRejected(expectedMessage: String, vararg entries: Pair<String, ByteArray>) {
        val archiveBytes = zipBytes(*entries)
        withTempDir { stagingDir ->
            val error = assertThrows(ImportValidationException::class.java) {
                NoteImportArchive.stageValidatedArchive(archiveBytes.inputStream(), stagingDir)
            }
            assertEquals(expectedMessage, error.message)
        }
    }

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    /** Two entries named notes.json — ZipOutputStream refuses that, so patch the bytes. */
    private fun duplicateNotesArchive(): ByteArray {
        val archive = zipBytes(
            "notes0json" to """{ "notes": [] }""".toByteArray(),
            "notes1json" to """{ "notes": [] }""".toByteArray(),
        )
        return archive
            .replaceAscii("notes0json", "notes.json")
            .replaceAscii("notes1json", "notes.json")
    }

    private fun withTempDir(block: (File) -> Unit) {
        val dir = File.createTempFile("note-import", "")
        dir.delete()
        dir.mkdirs()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun ByteArray.replaceAscii(from: String, to: String): ByteArray {
        require(from.length == to.length)
        val fromBytes = from.encodeToByteArray()
        val toBytes = to.encodeToByteArray()
        var index = 0
        while (index <= size - fromBytes.size) {
            var matches = true
            for (offset in fromBytes.indices) {
                if (this[index + offset] != fromBytes[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                for (offset in toBytes.indices) {
                    this[index + offset] = toBytes[offset]
                }
                index += toBytes.size
            } else {
                index++
            }
        }
        return this
    }
}
