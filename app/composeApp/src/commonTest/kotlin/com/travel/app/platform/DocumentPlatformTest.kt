package com.travel.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocumentPlatformTest {
    @Test
    fun sha256MatchesPublishedKnownAnswers() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", documentSha256(byteArrayOf()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", documentSha256("abc".encodeToByteArray()))
    }

    @Test
    fun storageRejectsPathsAndUnboundedIdentifiers() {
        listOf("", ".", "..", "../private.pdf", "/tmp/private.pdf", "folder/document", "folder\\document", "line\nbreak", "x".repeat(181)).forEach { unsafe ->
            assertFailsWith<IllegalArgumentException>(unsafe) { requireLocalFileKey(unsafe) }
        }
        requireLocalFileKey("document-6f87c8e2_abc.pdf")
    }

    @Test
    fun pickerUsesBytesInsteadOfProviderFilenameForMediaType() {
        val pngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        val selected = pickedDocument("provider-labeled.pdf", pngSignature)
        assertEquals("image/png", selected.mediaType)
        assertEquals("provider-labeled.pdf", selected.name)
        assertEquals("application/pdf", pickedDocument("ticket.pdf", "%PDF-1.7\n".encodeToByteArray()).mediaType)
        assertEquals("image/jpeg", pickedDocument("ticket.jpeg", byteArrayOf(255.toByte(), 216.toByte(), 255.toByte(), 224.toByte())).mediaType)
    }

    @Test
    fun pickerRejectsUnsupportedOrOversizedBytesAndUnsafeNames() {
        assertFailsWith<IllegalArgumentException> { pickedDocument("empty.pdf", byteArrayOf()) }
        assertFailsWith<IllegalStateException> { pickedDocument("fake.pdf", "this is text".encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { pickedDocument("large.pdf", ByteArray(MAX_DOCUMENT_BYTES + 1)) }
        listOf("../ticket.pdf", "/ticket.pdf", "folder\\ticket.pdf", "ticket\n.pdf", "x".repeat(201)).forEach { unsafe ->
            assertFailsWith<IllegalArgumentException>(unsafe) { pickedDocument(unsafe, "%PDF-1.7\n".encodeToByteArray()) }
        }
    }
}
