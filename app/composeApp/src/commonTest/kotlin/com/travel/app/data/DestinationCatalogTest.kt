package com.travel.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DestinationCatalogTest {
    private val countries = "JP\t일본\tJapan\tJP|JPN|일본|Japan\nUS\t미국\tUnited States\tUS|USA|미국|United States|United States of America\n"
    private val places = "city:1\tJP\t도쿄\tTokyo\t도쿄도\tcity\t도쿄|Tokyo|Tōkyō\n" +
        "city:2\tJP\t후추\tFuchu\t도쿄도\tcity\t후추|Fuchu\n" +
        "city:3\tJP\t후추\tFuchu\t히로시마현\tcity\t후추|Fuchu\n" +
        "region:JP.40\tJP\t도쿄도\tTokyo\t\tregion\t도쿄도|Tokyo\n" +
        "city:4\tUS\t도쿄\tTokyo\tTexas\tcity\t도쿄|Tokyo\n"

    @Test fun countryMatchingAndSearchIgnoreWhitespaceAndCaseWithoutChangingKorean() {
        val catalog = DestinationCatalog.fromTsv(countries, places)
        assertEquals("US", catalog.matchCountry("  united  STATES ")?.code)
        assertEquals("JP", catalog.matchCountry("JpN")?.code)
        assertEquals("JP", catalog.matchCountry("일 본")?.code)
        assertNull(catalog.matchCountry("Unlisted place"))
        assertEquals(listOf("US"), catalog.searchCountries(" united states ").map { it.code })
        assertEquals(2, catalog.searchCountries(" ").size)
    }

    @Test fun placesAreCountryScopedAndRetainDistinctIdsAndRegionContext() {
        val catalog = DestinationCatalog.fromTsv(countries, places)
        assertEquals(listOf("city:1", "region:JP.40"), catalog.searchPlaces("jp", "TOK YO").map { it.id })
        assertEquals(listOf("city:2", "city:3"), catalog.searchPlaces("JP", "후 추").map { it.id })
        assertEquals(listOf("도쿄도", "히로시마현"), catalog.searchPlaces("JP", "후추").map { it.region })
        assertEquals(listOf("region:JP.40", "city:1", "city:2"), catalog.searchPlaces("JP", "도쿄도").map { it.id })
        assertEquals(listOf("city:1", "city:2", "city:3", "region:JP.40"), catalog.searchPlaces("JP", "").map { it.id })
        assertEquals(listOf("city:4"), catalog.searchPlaces("US", "").map { it.id })
        assertEquals(listOf("city:4"), catalog.searchPlaces("US", "도쿄").map { it.id })
        assertEquals(emptyList(), catalog.searchPlaces("ZZ", ""))
    }

    @Test fun searchDoesNotTruncateMatchingResults() {
        val manyPlaces = (1..250).joinToString("\n") { "city:$it\tJP\t장소 $it\tPlace $it\tRegion\tcity\tPlace $it" }
        val catalog = DestinationCatalog.fromTsv(countries, manyPlaces)
        assertEquals(250, catalog.searchPlaces("JP", "Place").size)
        assertEquals(250, catalog.searchPlaces("JP", "").size)
    }

    @Test fun malformedResourceFailsBeforeReturningAnIncompleteCatalog() {
        assertFailsWith<IllegalArgumentException> { DestinationCatalog.fromTsv(countries, places + "bad\trow") }
        assertFailsWith<IllegalArgumentException> { DestinationCatalog.fromTsv(countries, places + places) }
        assertFailsWith<IllegalArgumentException> { DestinationCatalog.fromTsv(countries, places.replace("\tcity\t", "\tvillage\t")) }
    }
}
