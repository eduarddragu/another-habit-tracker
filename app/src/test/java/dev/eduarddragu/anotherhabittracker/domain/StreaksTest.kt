package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreaksTest {
  private val today = LocalDate.of(2026, 10, 10)

  private fun days(vararg ago: Long) = ago.map { today.minusDays(it) }.toSet()

  @Test
  fun emptyHistoryHasNoStreak() = assertEquals(0, Streaks.current(emptySet(), emptySet(), today))

  @Test
  fun countsConsecutiveDaysIncludingToday() = assertEquals(3, Streaks.current(days(0, 1, 2), emptySet(), today))

  @Test
  fun openTodayCountsUpToYesterday() = assertEquals(2, Streaks.current(days(1, 2), emptySet(), today))

  @Test
  fun gapBreaksTheStreak() {
    assertEquals(2, Streaks.current(days(0, 1, 3, 4), emptySet(), today))
    assertEquals(0, Streaks.current(days(2, 3), emptySet(), today))
  }

  @Test
  fun freezeKeepsTheStreakWithoutAddingToIt() {
    assertEquals(2, Streaks.current(days(0, 2), days(1), today))
    assertEquals(1, Streaks.current(days(1), days(0), today))
  }

  @Test
  fun theFreezeWeekCrossesTheNewYear() {
    // Thursday 2026-12-31 and Saturday 2027-01-02 share ISO week 53 of 2026; Monday 2027-01-04 starts week 1.
    val freezes = setOf(LocalDate.of(2026, 12, 31))
    assertFalse(Freezes.canFreeze(LocalDate.of(2027, 1, 2), emptySet(), freezes))
    assertTrue(Freezes.canFreeze(LocalDate.of(2027, 1, 4), emptySet(), freezes))
  }

  @Test
  fun yesterdayOnMondayUsesLastWeeksFreeze() {
    // Monday 2026-10-12: yesterday (Sunday) belongs to the week whose freeze went on Wednesday.
    val monday = LocalDate.of(2026, 10, 12)
    val sessions = setOf(LocalDate.of(2026, 10, 10))
    assertFalse(Freezes.canSaveYesterday(monday, sessions, setOf(LocalDate.of(2026, 10, 7))))
    assertTrue(Freezes.canSaveYesterday(monday, sessions, emptySet()))
  }

  @Test
  fun thirtyDayWindowAcrossALeapFebruary() {
    val today = LocalDate.of(2028, 3, 1)
    val stats = Stats.of(listOf(Session(LocalDate.of(2028, 2, 1), null, 10), Session(LocalDate.of(2028, 1, 31), null, 20)), emptySet(), today)
    assertEquals(1, stats.daysLast30)
    assertEquals(10, stats.minutesLast30)
  }

  @Test
  fun oneFreezePerIsoWeek() {
    val monday = LocalDate.of(2026, 10, 5)
    assertTrue(Freezes.canFreeze(monday, emptySet(), emptySet()))
    assertFalse(Freezes.canFreeze(monday.plusDays(3), emptySet(), setOf(monday)))
    assertTrue(Freezes.canFreeze(monday.plusDays(7), emptySet(), setOf(monday)))
  }

  @Test
  fun cannotFreezeADayAlreadyLogged() = assertFalse(Freezes.canFreeze(today, setOf(today), emptySet()))
}
