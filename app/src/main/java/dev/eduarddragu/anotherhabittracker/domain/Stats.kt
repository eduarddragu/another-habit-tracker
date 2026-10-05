package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate

/** A logged session reduced to what the numbers need. */
data class Session(val day: LocalDate, val score: Int?, val minutes: Int?)

data class HabitStats(
  val streak: Int,
  /** Distinct days with a session in the last 30 days, today included. */
  val daysLast30: Int,
  val minutesLast30: Int,
  /** Average score of scored sessions in the last 30 days, null when there are none. */
  val averageScoreLast30: Double?,
  val totalSessions: Int,
  val totalMinutes: Int,
)

object Stats {
  fun of(sessions: List<Session>, freezes: Set<LocalDate>, today: LocalDate): HabitStats {
    val days = sessions.map { it.day }.toSet()
    val since30 = today.minusDays(29)
    val recent = sessions.filter { !it.day.isBefore(since30) && !it.day.isAfter(today) }
    val scores = recent.mapNotNull { it.score }
    return HabitStats(
      streak = Streaks.current(days, freezes, today),
      daysLast30 = recent.map { it.day }.toSet().size,
      minutesLast30 = recent.sumOf { it.minutes ?: 0 },
      averageScoreLast30 = scores.takeIf { it.isNotEmpty() }?.average(),
      totalSessions = sessions.size,
      totalMinutes = sessions.sumOf { it.minutes ?: 0 },
    )
  }
}
