package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate

/**
 * A stretch of days off (a loss in the family, an illness, a move): from [start], which may be in the
 * past, to [end] included, or open while [end] is null. Days off neither count nor break a streak:
 * they bridge it, like a freeze that doesn't use up the week's. Nothing nags on them (no reminders,
 * no guard, no recap); a session logged on one still counts.
 */
data class TimeOffPeriod(val id: Long = 0, val start: LocalDate, val end: LocalDate? = null) {
  fun contains(day: LocalDate): Boolean = !day.isBefore(start) && (end == null || !day.isAfter(end))
}

object TimeOff {
  /** Every day off up to [today] (an open period runs through today). */
  fun days(periods: List<TimeOffPeriod>, today: LocalDate): Set<LocalDate> =
    periods
      .flatMap { period ->
        val last = minOf(period.end ?: today, today)
        generateSequence(period.start) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
      }
      .toSet()

  /** The period [today] falls in, if any. */
  fun current(periods: List<TimeOffPeriod>, today: LocalDate): TimeOffPeriod? = periods.firstOrNull { it.contains(today) }

  /**
   * The first day of the latest time off that study hasn't come back from yet: no session on or
   * after it. Its topic is held through the time off and offered again on return, until logged.
   */
  fun heldSince(periods: List<TimeOffPeriod>, today: LocalDate, sessionDays: Set<LocalDate>): LocalDate? {
    val latest = periods.filter { !it.start.isAfter(today) }.maxByOrNull { it.start } ?: return null
    return latest.start.takeIf { start -> sessionDays.none { !it.isBefore(start) } }
  }

  /** How far back a period may start: enough to cover what was missed, not to rewrite history. */
  const val MAX_DAYS_BACK = 60L
}
