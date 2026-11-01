package com.travel.app.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalUrlsTest {
    @Test fun acceptsPublicWebSearchLinks() {
        assertTrue(isExternalWebUrl("https://www.google.com/travel/flights?q=Flights%20from%20ICN%20to%20KIX&hl=ko"))
        assertTrue(isExternalWebUrl("http://example.com/search"))
    }
    @Test fun rejectsNonWebSchemes() {
        listOf("javascript:alert(1)", "file:///tmp/test", "intent://travel", "data:text/plain,test").forEach {
            assertFalse(isExternalWebUrl(it), it)
        }
    }
    @Test fun rejectsMissingAuthorityOrScheme() {
        listOf("", "https://", "//example.com", "example.com", "/travel/flights").forEach {
            assertFalse(isExternalWebUrl(it), it)
        }
    }
    @Test fun rejectsCredentialsAndWhitespace() {
        listOf("https://user:secret@example.com", "https://example.com/path with spaces", "https://example.com\n").forEach {
            assertFalse(isExternalWebUrl(it), it)
        }
    }
}
