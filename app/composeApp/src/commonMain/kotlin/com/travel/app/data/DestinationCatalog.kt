package com.travel.app.data

import com.travel.app.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

data class DestinationCountry(val code: String, val name: String, val englishName: String)

data class DestinationPlace(
    val id: String,
    val countryCode: String,
    val name: String,
    val englishName: String,
    val region: String,
    val kind: String,
)

/** Offline, attributed country/territory, city and first-level region index. */
class DestinationCatalog private constructor(
    countryEntries: List<CountryEntry>,
    placeEntries: List<PlaceEntry>,
) {
    private val countryEntries = countryEntries.sortedBy { it.country.name }
    val countries: List<DestinationCountry> = this.countryEntries.map { it.country }
    private val countriesByAlias = buildMap {
        this@DestinationCatalog.countryEntries.forEach { entry ->
            entry.aliases.forEach { alias -> if (alias !in this) put(alias, entry.country) }
        }
    }
    private val placesByCountry = placeEntries.groupBy { it.place.countryCode }

    fun searchCountries(query: String): List<DestinationCountry> {
        val needle = normalize(query)
        if (needle.isEmpty()) return countries
        return countryEntries.filter { entry -> entry.aliases.any { needle in it } }
            .sortedBy { entry -> rank(needle, entry.country.name, entry.country.englishName, entry.aliases) }
            .map { it.country }
    }

    /** Empty queries retain the generator's population order, followed by regions. */
    fun searchPlaces(countryCode: String, query: String): List<DestinationPlace> {
        val entries = placesByCountry[countryCode.trim().uppercase()].orEmpty()
        val needle = normalize(query)
        if (needle.isEmpty()) return entries.map { it.place }
        return entries.filter { needle in it.searchText }
            .sortedBy { entry -> rank(needle, entry.place.name, entry.place.englishName, entry.aliases) }
            .map { it.place }
    }

    /** Resolve existing saved country labels and ISO codes by exact alias. */
    fun matchCountry(value: String): DestinationCountry? = countriesByAlias[normalize(value)]

    private data class CountryEntry(val country: DestinationCountry, val aliases: List<String>)
    private data class PlaceEntry(val place: DestinationPlace, val aliases: List<String>, val searchText: String)

    companion object {
        private val loadMutex = Mutex()
        private var cached: DestinationCatalog? = null

        /** One shared cache across editor mounts; parsing never runs on the UI thread. */
        @OptIn(ExperimentalResourceApi::class)
        suspend fun load(): DestinationCatalog = loadMutex.withLock {
            cached ?: withContext(Dispatchers.Default) {
                fromTsv(
                    Res.readBytes("files/destinations/countries.tsv").decodeToString(),
                    Res.readBytes("files/destinations/places.tsv").decodeToString(),
                )
            }.also { cached = it }
        }

        internal fun fromTsv(countriesTsv: String, placesTsv: String): DestinationCatalog {
            val countryEntries = rows(countriesTsv, 4).map { fields ->
                val country = DestinationCountry(fields[0], fields[1], fields[2])
                require(country.code.length == 2 && country.name.isNotBlank()) { "Invalid destination country" }
                CountryEntry(country, aliases(fields[3], country.code, country.name, country.englishName))
            }
            require(countryEntries.map { it.country.code }.distinct().size == countryEntries.size) { "Duplicate destination country code" }
            val countryCodes = countryEntries.map { it.country.code }.toSet()
            val placeEntries = rows(placesTsv, 7).map { fields ->
                val place = DestinationPlace(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5])
                require(place.id.isNotBlank() && place.name.isNotBlank()) { "Invalid destination place" }
                require(place.kind == "city" || place.kind == "region") { "Invalid destination place kind" }
                require(place.countryCode in countryCodes) { "Unknown destination country" }
                val aliases = aliases(fields[6], place.name, place.englishName)
                // Delimiters keep a query from spuriously crossing alias boundaries.
                PlaceEntry(place, aliases, (aliases + normalize(place.region)).joinToString("\u0000"))
            }
            require(placeEntries.map { it.place.id }.distinct().size == placeEntries.size) { "Duplicate destination place ID" }
            return DestinationCatalog(countryEntries, placeEntries)
        }

        private fun rows(tsv: String, columnCount: Int): List<List<String>> =
            tsv.lineSequence().filter { it.isNotBlank() }.map { line ->
                line.trimEnd('\r').split('\t').also {
                    require(it.size == columnCount) { "Malformed destination resource: expected $columnCount columns" }
                }
            }.toList()

        private fun aliases(values: String, vararg primary: String): List<String> =
            (primary.toList() + values.split('|')).map(::normalize).filter { it.isNotEmpty() }.distinct()

        private fun normalize(value: String): String = value.filterNot { it.isWhitespace() }.lowercase()

        private fun rank(query: String, name: String, englishName: String, aliases: List<String>): Int = when {
            query == normalize(name) || query == normalize(englishName) -> 0
            query in aliases -> 1
            normalize(name).startsWith(query) || normalize(englishName).startsWith(query) -> 2
            aliases.any { it.startsWith(query) } -> 3
            else -> 4
        }
    }
}
