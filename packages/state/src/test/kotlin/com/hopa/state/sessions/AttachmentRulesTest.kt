package com.hopa.state.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentRulesTest {
    private fun file(name: String, mime: String) =
        Attachment(name = name, mime = mime, bytes = 1024, path = "/tmp/$name")

    @Test
    fun an_image_is_described_as_an_image() {
        assertEquals(
            "Shared an image: cat.jpg",
            AttachmentRules.describe(file("cat.jpg", "image/jpeg")),
        )
    }

    @Test
    fun anything_else_is_a_file() {
        assertEquals(
            "Shared a file: report.pdf",
            AttachmentRules.describe(file("report.pdf", "application/pdf")),
        )
        assertFalse(AttachmentRules.isImage("application/pdf"))
        assertTrue(AttachmentRules.isImage("image/png"))
    }

    @Test
    fun sizes_read_the_way_a_person_says_them() {
        assertEquals("900 B", AttachmentRules.humanSize(900))
        assertEquals("4 KB", AttachmentRules.humanSize(4 * 1024))
        assertEquals("2.5 MB", AttachmentRules.humanSize((2.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun the_cap_is_four_mebibytes() {
        assertEquals(4L * 1024 * 1024, AttachmentRules.MAX_BYTES)
    }
}
