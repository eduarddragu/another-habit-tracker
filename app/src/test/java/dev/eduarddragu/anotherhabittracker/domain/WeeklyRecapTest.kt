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
    assertEquals("Meditation: 4 of 6 days, 40 min.\nStill open tonight: Meditation. The week isn't over.", text.body)
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
    assertEquals("Meditation: 1 of 1 days, 10 min.\nStill open tonight: Meditation. The week isn't over.\nLongest streak: 1 day.", text.body)
  }

  @Test
  fun theHabitThatShowedUpLeastLeadsNextWeek() {
    val sunday = LocalDate.of(2027, 1, 3)
    val monday = sunday.minusDays(6)
    val study = RecapHabit("Study", HabitKind.SIMPLE, (0L..6L).map { session(monday.plusDays(it), 30) }, streak = 7, since = monday)
    val chores = RecapHabit("Chores", HabitKind.SIMPLE, listOf(session(monday, 15), session(sunday, 15)), streak = 1, since = monday)
    val text = WeeklyRecap.build(listOf(study, chores), sunday) { null }
    assertTrue(text.body.endsWith("Next week: Chores first."))
    assertTrue("nothing open tonight", "Still open" !in text.body)
    val even = WeeklyRecap.build(listOf(study, study.copy(name = "Reading")), sunday) { null }
    assertTrue("no leader when every habit did the same", "Next week" !in even.body)
  }

  @Test
  fun habitsStillOpenTonightAreNamed() {
    val sunday = LocalDate.of(2027, 1, 3)
    val a = RecapHabit("Study", HabitKind.SIMPLE, listOf(session(sunday.minusDays(1), 30)), streak = 1, since = sunday.minusDays(1))
    val text = WeeklyRecap.build(listOf(a, a.copy(name = "Reading"), a.copy(name = "Chores")), sunday) { null }
    assertTrue(text.body.contains("Still open tonight: Study, Reading and Chores. The week isn't over."))
    val off = WeeklyRecap.build(listOf(a), sunday, paused = setOf(sunday)) { null }
    assertTrue("a day off isn't open", "Still open" !in off.body)
  }

  @Test
  fun noHabitsMeansAnEmptyRecap() {
    val text = WeeklyRecap.build(emptyList(), LocalDate.of(2027, 1, 3)) { null }
    assertEquals("", text.body)
    assertTrue(text.title.isNotBlank())
  }
}
