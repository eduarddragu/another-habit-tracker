package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyRecapTest {
  private fun session(day: LocalDate, minutes: Int) = LogRecord(day, EntryType.SESSION, minutes = minutes)

  @Test
  fun aFrozenDayCountsAsMissed() {
    // Sunday 2027-01-03: sessions Monday to Thursday, a freeze on Friday, nothing on Saturday.
    val sunday = LocalDate.of(2027, 1, 3)
    val records = (0L..3L).map { session(LocalDate.of(2026, 12, 28).plusDays(it), 10) } + LogRecord(LocalDate.of(2027, 1, 1), EntryType.FREEZE)
    val text = WeeklyRecap.build(listOf(RecapHabit("Meditation", HabitKind.SIMPLE, records, streak = 0)), sunday) { null }
    assertEquals("Meditation: 4 of 7 days, 40 min.", text.body)
  }

  @Test
  fun twoSessionsOnOneDayAreOneDayWithTheirMinutesAdded() {
    val monday = LocalDate.of(2026, 12, 28)
    val records = listOf(session(monday, 10), session(monday, 15))
    val text = WeeklyRecap.build(listOf(RecapHabit("Meditation", HabitKind.SIMPLE, records, streak = 1)), monday) { null }
    assertEquals("Meditation: 1 of 1 days, 25 min.\nLongest streak: 1 day.", text.body)
  }

  @Test
  fun aHabitNeverLoggedStaysOutOfTheRecap() {
    val sunday = LocalDate.of(2027, 1, 3)
    val meditation = RecapHabit("Meditation", HabitKind.SIMPLE, listOf(session(sunday.minusDays(1), 10)), streak = 1, since = sunday.minusDays(1))
    val chores = RecapHabit("Chores", HabitKind.SIMPLE, emptyList(), streak = 0)
    val text = WeeklyRecap.build(listOf(meditation, chores), sunday) { null }
    assertEquals("Meditation: 1 of 2 days, 10 min.\nLongest streak: 1 day.", text.body)
  }

  @Test
  fun theHabitThatShowedUpLeastLeadsNextWeek() {
    val sunday = LocalDate.of(2027, 1, 3)
    val monday = sunday.minusDays(6)
    val study = RecapHabit("Study", HabitKind.SIMPLE, (0L..6L).map { session(monday.plusDays(it), 30) }, streak = 7, since = monday)
    val chores = RecapHabit("Chores", HabitKind.SIMPLE, listOf(session(monday, 15), session(sunday, 15)), streak = 1, since = monday)
    val text = WeeklyRecap.build(listOf(study, chores), sunday) { null }
    assertTrue(text.body.endsWith("Next week: Chores first."))
    val even = WeeklyRecap.build(listOf(study, study.copy(name = "Reading")), sunday) { null }
    assertTrue("no leader when every habit did the same", "Next week" !in even.body)
  }

  @Test
  fun theWeekIsClosedSoAnEmptySundayIsMissed() {
    val sunday = LocalDate.of(2027, 1, 3)
    val a = RecapHabit("Study", HabitKind.SIMPLE, listOf(session(sunday.minusDays(1), 30)), streak = 1, since = sunday.minusDays(1))
    assertTrue(WeeklyRecap.build(listOf(a), sunday) { null }.body.startsWith("Study: 1 of 2 days"))
    assertTrue("a day off isn't missed", WeeklyRecap.build(listOf(a), sunday, paused = setOf(sunday)) { null }.body.startsWith("Study: 1 of 1 days"))
  }

  @Test
  fun noRecapOnTimeOffNorForAWeekSpentOnIt() {
    val sunday = LocalDate.of(2027, 1, 3)
    assertTrue("off today", WeeklyRecap.skip(pausedToday = true, daysOff = emptySet(), lastDay = sunday))
    assertTrue("the whole week off", WeeklyRecap.skip(pausedToday = false, daysOff = (0L..6L).map { sunday.minusDays(it) }.toSet(), lastDay = sunday))
    assertEquals("part of the week off", false, WeeklyRecap.skip(pausedToday = false, daysOff = setOf(sunday, sunday.minusDays(1)), lastDay = sunday))
  }

  @Test
  fun noHabitsMeansAnEmptyRecap() {
    val text = WeeklyRecap.build(emptyList(), LocalDate.of(2027, 1, 3)) { null }
    assertEquals("", text.body)
    assertTrue(text.title.isNotBlank())
  }
}
