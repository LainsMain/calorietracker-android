package be.calorietracker.domain

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.serialization.Serializable

@Serializable
data class FastingWindow(
  val enabled: Boolean = false,
  val starts: String = "12:00",
  val ends: String = "20:00",
) {
  fun validate() {
    require(LocalTime.parse(starts) != LocalTime.parse(ends)) { "Choose different start and end times." }
  }

  fun status(now: ZonedDateTime = ZonedDateTime.now()): FastingStatus {
    validate()
    val start = LocalTime.parse(starts)
    val end = LocalTime.parse(ends)
    val eating = if (start < end) now.toLocalTime() >= start && now.toLocalTime() < end
      else now.toLocalTime() >= start || now.toLocalTime() < end
    val next = (0L..2L).flatMap { offset ->
      val date = now.toLocalDate().plusDays(offset)
      listOf(start, end).flatMap { time ->
        val local = LocalDateTime.of(date, time)
        val offsets = now.zone.rules.getValidOffsets(local)
        if (offsets.isEmpty()) listOf(local.atZone(now.zone))
        else offsets.map { ZonedDateTime.ofLocal(local, now.zone, it) }
      }
    }.filter { it.isAfter(now) }.minBy { it.toInstant() }
    return FastingStatus(eating, next)
  }
}

data class FastingStatus(val canEat: Boolean, val nextTransition: ZonedDateTime)
