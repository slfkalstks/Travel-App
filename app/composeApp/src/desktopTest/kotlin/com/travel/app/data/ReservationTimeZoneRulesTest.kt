package com.travel.app.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.zone.ZoneOffsetTransition
import java.time.zone.ZoneRules
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Independent JVM oracle for the common implementation's hourly offset-candidate sampling.
 *
 * nextTransition includes recurring future rules, unlike ZoneRules.transitions alone. All zone
 * IDs receive normal-clock checks; equal JDK ZoneRules are traversed once so aliases do not repeat
 * the same transition history. For T transitions across distinct rule sets and Z IDs, there are
 * at most 5T + 3Z helper calls, each currently doing 97 offsetAt lookups (O(97 * (T + Z))).
 * This deliberately exhaustive test belongs to desktopTest and does not burden mobile tests.
 */
class ReservationTimeZoneRulesTest {
    @Test
    fun everyDistinctJvmRuleSetMatchesAtTransitionBoundariesFrom1900Through2100() {
        val zones = TimeZone.availableZoneIds.sorted()
        assertEquals(ZoneId.getAvailableZoneIds(), zones.toSet(), "The JVM oracle must cover every selectable zone")

        // Test every ID, including aliases, while keeping expensive transition traversal per rule set.
        val normalProbes = listOf(
            LocalDateTime.of(1900, 1, 15, 12, 0),
            LocalDateTime.of(2000, 7, 15, 12, 0),
            LocalDateTime.of(2100, 12, 15, 12, 0),
        )
        zones.forEach { zoneId ->
            val rules = ZoneId.of(zoneId).rules
            normalProbes.forEach { assertOffsetsAgree(zoneId, rules, it, "zone-ID coverage") }
        }

        val ruleSets = zones.groupBy { ZoneId.of(it).rules }
        var transitionsChecked = 0
        var transitionProbesChecked = 0
        var gapProbesChecked = 0
        var overlapProbesChecked = 0
        var sawSecondPrecisionOffset = false
        var sawNonHourTransition = false
        var sawDateLineTransition = false

        ruleSets.forEach { (rules, aliases) ->
            val zoneId = aliases.first()
            // Padding covers boundary-year local dates even when their UTC instant is outside it.
            var cursor = Instant.parse("1899-12-30T00:00:00Z")
            val until = Instant.parse("2101-01-03T00:00:00Z")
            while (true) {
                val transition = rules.nextTransition(cursor) ?: break
                assertTrue(transition.instant > cursor, "nextTransition must advance: $zoneId $transition")
                if (transition.instant >= until) break
                cursor = transition.instant
                val probes = minuteProbes(transition).filter { it.year in 1900..2100 }
                if (probes.isEmpty()) continue

                transitionsChecked++
                val changeSeconds = transition.duration.seconds
                sawSecondPrecisionOffset = sawSecondPrecisionOffset ||
                    transition.offsetBefore.totalSeconds % 60 != 0 || transition.offsetAfter.totalSeconds % 60 != 0
                sawNonHourTransition = sawNonHourTransition || abs(changeSeconds) % 3600L != 0L
                sawDateLineTransition = sawDateLineTransition || abs(changeSeconds) >= 86400L

                probes.forEach { local ->
                    val expectedCount = rules.getValidOffsets(local).size
                    if (expectedCount == 0) gapProbesChecked++
                    if (expectedCount > 1) overlapProbesChecked++
                    assertOffsetsAgree(zoneId, rules, local, transition.toString())
                    transitionProbesChecked++
                }
            }
        }

        // These guards ensure the oracle actually exercised the difficult historical cases.
        assertTrue(transitionsChecked > 0, "No transitions were traversed")
        assertTrue(gapProbesChecked > 0, "No minute-representable gap was checked")
        assertTrue(overlapProbesChecked > 0, "No minute-representable overlap was checked")
        assertTrue(sawSecondPrecisionOffset, "Historical second-precision offsets were not covered")
        assertTrue(sawNonHourTransition, "Non-hour clock changes were not covered")
        assertTrue(sawDateLineTransition, "A date-line clock change was not covered")
        println(
            "Reservation zone oracle: ${zones.size} zone IDs, ${ruleSets.size} distinct rule sets, " +
                "$transitionsChecked transitions, $transitionProbesChecked transition probes, " +
                "$gapProbesChecked gap probes, $overlapProbesChecked overlap probes; " +
                "${97L * (transitionProbesChecked + 3L * zones.size)} common offset lookups",
        )
    }

    private fun assertOffsetsAgree(zoneId: String, rules: ZoneRules, local: LocalDateTime, context: String) {
        val expected = rules.getValidOffsets(local)
            .sortedBy { local.toInstant(it) }
            .map { it.id }
        val actual = validReservationOffsets(local.toLocalDate().toString(), local.toLocalTime().toString(), zoneId)
        assertEquals(expected, actual, "$zoneId at $local; $context")
    }

    /** Closest representable minute before/after, plus first/middle/last interior minute. */
    private fun minuteProbes(transition: ZoneOffsetTransition): List<LocalDateTime> {
        val lower = minOf(transition.dateTimeBefore, transition.dateTimeAfter)
        val upper = maxOf(transition.dateTimeBefore, transition.dateTimeAfter)
        val firstInside = ceilToMinute(lower)
        val firstAfter = ceilToMinute(upper)
        val midpoint = lower.plusSeconds(Duration.between(lower, upper).seconds / 2)
            .truncatedTo(ChronoUnit.MINUTES)
        val interior = listOf(firstInside, midpoint, firstAfter.minusMinutes(1))
            .filter { it >= lower && it < upper }
        // A historical change shorter than one minute may have no HH:mm value inside it.
        return (listOf(firstInside.minusMinutes(1)) + interior + firstAfter).distinct()
    }

    private fun ceilToMinute(value: LocalDateTime): LocalDateTime {
        val floor = value.truncatedTo(ChronoUnit.MINUTES)
        return if (floor == value) floor else floor.plusMinutes(1)
    }
}
