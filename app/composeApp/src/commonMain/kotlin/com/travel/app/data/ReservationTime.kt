package com.travel.app.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours

/** Reconstruct only offsets that map the entered wall clock back to itself. Never normalize a gap. */
fun validReservationOffsets(date: String, time: String, zoneId: String): List<String> {
    require(Regex("(?:19[0-9]{2}|20[0-9]{2}|2100)-[0-9]{2}-[0-9]{2}").matches(date)) { "올바른 날짜를 입력해 주세요" }
    require(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(time)) { "시간을 HH:mm 형식으로 입력해 주세요" }
    require(zoneId in TimeZone.availableZoneIds) { "목록에서 시간대를 선택해 주세요" }
    val local = LocalDateTime.parse("${date}T$time")
    val zone = TimeZone.of(zoneId)
    val nominal = local.toInstant(TimeZone.UTC)
    // The same local time can map to two UTC instants at an overlap. Offset candidates on both
    // sides of nearby rule transitions are checked by a full round trip, including historical seconds.
    return (-48..48).map { zone.offsetAt(nominal + it.hours) }.distinct()
        .map { offset -> offset to local.toInstant(offset) }
        .filter { (_, instant) -> instant.toLocalDateTime(zone) == local }
        .sortedBy { it.second }
        .map { it.first.toString() }
}

fun reservationInstant(date: String, time: String, zoneId: String, utcOffset: String = ""): kotlin.time.Instant {
    val offsets = validReservationOffsets(date, time, zoneId)
    require(offsets.isNotEmpty()) { "이 시간대에는 존재하지 않는 시각이에요. 예약의 현지 시간을 확인해 주세요" }
    require(utcOffset.isNotBlank() || offsets.size == 1) { "같은 시각이 두 번 있어요. 예약에 표시된 UTC 오프셋을 선택해 주세요" }
    val selected = if(utcOffset.isBlank()) offsets.single() else UtcOffset.parse(utcOffset).toString()
    require(selected in offsets) { "선택한 UTC 오프셋이 이 날짜의 시간대와 맞지 않아요" }
    return LocalDateTime.parse("${date}T$time").toInstant(UtcOffset.parse(selected))
}
