package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * Streak rules, per habit:
 * - a day with at least one session counts;
 * - a frozen day keeps the streak alive without adding to it;
 * - while today is still open, the streak runs up to yesterday.
 */
object Streaks {
  fun current(sessions: Set<LocalDate>, freezes: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in sessions || today in freezes) today else today.minusDays(1)
    var count = 0
    while (day in sessions || day in freezes) {
      if (day in sessions) count++
      day = day.minusDays(1)
    }
    return count
  }

  fun longest(sessions: Set<LocalDate>, freezes: Set<LocalDate>): Int {
    var best = 0
    var run = 0
    var previous: LocalDate? = null
    for (day in (sessions + freezes).sorted()) {
      if (previous == null || previous.plusDays(1) != day) run = 0
      if (day in sessions) run++
      best = maxOf(best, run)
      previous = day
    }
    return best
  }
}

/** One freeze per ISO week, and only on a day without a session or another freeze. */
object Freezes {
  fun canFreeze(day: LocalDate, sessions: Set<LocalDate>, freezes: Set<LocalDate>): Boolean {
    if (day in sessions || day in freezes) return false
    return freezes.none { isoWeek(it) == isoWeek(day) }
  }

  /** The day this week's freeze went on, if it's used. */
  fun usedThisWeek(today: LocalDate, freezes: Set<LocalDate>): LocalDate? = freezes.firstOrNull { isoWeek(it) == isoWeek(today) }

  /**
   * Yesterday can still be frozen after the fact: it has nothing logged, this week's freeze (of
   * yesterday's week) is free, and the day before had something, so there is a streak to save.
   */
  fun canSaveYesterday(today: LocalDate, sessions: Set<LocalDate>, freezes: Set<LocalDate>): Boolean {
    val yesterday = today.minusDays(1)
    val before = yesterday.minusDays(1)
    return canFreeze(yesterday, sessions, freezes) && (before in sessions || before in freezes)
  }

  /**
   * Yesterday has nothing at all (no session, no freeze) and the habit was already running then:
   * the habit's page offers to log it (done late, past midnight) and, when the rules allow, to
   * freeze it. Logging it is the only way to log for yesterday, so it doesn't depend on a streak.
   */
  fun yesterdayEmpty(today: LocalDate, sessions: Set<LocalDate>, freezes: Set<LocalDate>): Boolean {
    val yesterday = today.minusDays(1)
    return yesterday !in sessions && yesterday !in freezes && (sessions + freezes).any { it < yesterday }
  }

  private fun isoWeek(day: LocalDate) = day.get(IsoFields.WEEK_BASED_YEAR) to day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
}

/** "1 day", "12 days": every day count in the copy goes through here. */
fun dayCount(days: Int): String = if (days == 1) "1 day" else "$days days"
