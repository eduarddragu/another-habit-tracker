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
    assertEquals("Meditation: 4 of 6 days, 40 min.", text.body)
  }

  @Test
  fun twoSessionsOnOneDayAreOneDayWithTheirMinutesAdded() {
    val monday = LocalDate.of(2026, 12, 28)
    val records = listOf(session(monday, 10), session(monday, 15))
    val text = WeeklyRecap.build(listOf(RecapHabit("Meditation", HabitKind.SIMPLE, records, streak = 1)), monday) { null }
    assertEquals("Meditation: 1 of 1 days, 25 min.\nLongest streak: 1 day.", text.body)
  }

  @Test
  fun noHabitsMeansAnEmptyRecap() {
    val text = WeeklyRecap.build(emptyList(), LocalDate.of(2027, 1, 3)) { null }
    assertEquals("", text.body)
    assertTrue(text.title.isNotBlank())
  }
}
