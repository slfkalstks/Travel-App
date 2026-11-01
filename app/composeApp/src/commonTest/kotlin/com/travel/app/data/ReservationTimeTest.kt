package com.travel.app.data

import kotlin.test.*
import kotlin.time.Instant

class ReservationTimeTest {
    @Test fun normalClockKeepsItsZoneAndInfersOneOffset() {
        assertEquals(listOf("+09:00"),validReservationOffsets("2026-11-12","09:00","Asia/Tokyo"))
        assertEquals(Instant.parse("2026-11-12T00:00:00Z"),reservationInstant("2026-11-12","09:00","Asia/Tokyo"))
    }
    @Test fun daylightSavingGapIsRejectedInsteadOfMovingTheBooking() {
        assertEquals(emptyList(),validReservationOffsets("2026-03-08","02:30","America/New_York"))
        assertFailsWith<IllegalArgumentException>{reservationInstant("2026-03-08","02:30","America/New_York")}
    }
    @Test fun repeatedClockRequiresAnExplicitOccurrence() {
        assertEquals(listOf("-04:00","-05:00"),validReservationOffsets("2026-11-01","01:30","America/New_York"))
        assertFailsWith<IllegalArgumentException>{reservationInstant("2026-11-01","01:30","America/New_York")}
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"),reservationInstant("2026-11-01","01:30","America/New_York","-04:00"))
        assertEquals(Instant.parse("2026-11-01T06:30:00Z"),reservationInstant("2026-11-01","01:30","America/New_York","-05:00"))
    }
    @Test fun crossingDateLineCanArriveOnAnEarlierLocalDate() {
        val departure=reservationInstant("2026-11-15","01:00","Asia/Tokyo")
        val arrival=reservationInstant("2026-11-14","18:00","America/Los_Angeles")
        assertTrue(arrival>departure)
    }
    @Test fun malformedAndMismatchedZoneInputsCannotSilentlyBecomeUtc() {
        assertFailsWith<IllegalArgumentException>{validReservationOffsets("2026-02-30","09:00","Asia/Tokyo")}
        assertFailsWith<IllegalArgumentException>{validReservationOffsets("2026-11-12","24:00","Asia/Tokyo")}
        assertFailsWith<IllegalArgumentException>{validReservationOffsets("2026-11-12","09:00","Missing/Zone")}
        assertFailsWith<IllegalArgumentException>{reservationInstant("2026-11-12","09:00","Asia/Tokyo","+08:00")}
    }
}
