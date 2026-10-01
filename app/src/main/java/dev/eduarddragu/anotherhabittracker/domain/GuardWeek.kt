package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate

/** One day of the scroll guard: how many times it stepped in, and the minutes it let through. */
data class GuardDay(val day: LocalDate, val blocks: Int, val usedMillis: Long)

/** The guard's week (Monday to today), for its settings: a little honest accounting. */
object GuardWeek {
  data class Summary(val blocks: Int, val minutes: Int)

  fun summary(days: List<GuardDay>, today: LocalDate): Summary {
    val start = WeeklyRecap.weekStart(today)
    val week = days.filter { !it.day.isBefore(start) && !it.day.isAfter(today) }
    return Summary(week.sumOf { it.blocks }, (week.sumOf { it.usedMillis } / 60_000).toInt())
  }

  /** "Stopped you 12 times this week, 34 minutes let through." */
  fun line(summary: Summary): String =
    when {
      summary.blocks == 0 -> "Hasn't had to stop you this week."
      else -> "Stopped you ${summary.blocks} ${if (summary.blocks == 1) "time" else "times"} this week, ${summary.minutes} ${if (summary.minutes == 1) "minute" else "minutes"} let through."
    }

  /** Keeps the last [keep] days, so the store doesn't grow. */
  fun trim(days: List<GuardDay>, today: LocalDate, keep: Long = 14): List<GuardDay> = days.filter { it.day.isAfter(today.minusDays(keep)) }
}
