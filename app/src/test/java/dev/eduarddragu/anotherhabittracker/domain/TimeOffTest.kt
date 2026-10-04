package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeOffTest {
  private val today = LocalDate.of(2026, 10, 8) // a Thursday
  private fun session(day: LocalDate) = LogRecord(day, EntryType.SESSION, minutes = 30)

  @Test
  fun daysOffBridgeTheStreakWithoutCountingOrUsingTheFreeze() {
    // Sessions up to Oct 2, off from Oct 3 (retroactively) and still open.
    val records = (0L..4L).map { session(LocalDate.of(2026, 10, 2).minusDays(it)) }
    val off = TimeOff.days(listOf(TimeOffPeriod(start = LocalDate.of(2026, 10, 3))), today)
    val summary = HabitSummaries.build(HabitKind.SIMPLE, records, today, off) { error("") }
    assertEquals(5, summary.stats.streak)
    assertTrue(summary.pausedToday)
    assertFalse("nothing nags on a day off", summary.dayOpen)
    assertNull("the week's freeze is untouched", summary.freezeUsedOn)
    assertFalse(summary.yesterdayEmpty)
  }

  @Test
  fun aClosedPeriodEndsAndASessionOnADayOffStillCounts() {
    val records = listOf(session(LocalDate.of(2026, 10, 2)), session(LocalDate.of(2026, 10, 5)), session(today))
    val off = TimeOff.days(listOf(TimeOffPeriod(start = LocalDate.of(2026, 10, 3), end = LocalDate.of(2026, 10, 7))), today)
    assertFalse(today in off)
    val summary = HabitSummaries.build(HabitKind.SIMPLE, records, today, off) { error("") }
    assertEquals(3, summary.stats.streak)
    assertTrue(summary.doneToday)
  }

  @Test
  fun currentPeriod() {
    val open = TimeOffPeriod(start = today.minusDays(2))
    assertEquals(open, TimeOff.current(listOf(open), today))
    assertNull(TimeOff.current(listOf(TimeOffPeriod(start = today.minusDays(5), end = today.minusDays(1))), today))
  }

  @Test
  fun weekendTimesAndNextSlot() {
    val weekdays = listOf(LocalTime.of(13, 0), LocalTime.of(18, 30), LocalTime.of(21, 30))
    val weekend = listOf(LocalTime.of(10, 0), LocalTime.of(16, 0))
    val saturday = LocalDate.of(2026, 10, 10)
    assertEquals(weekend, ReminderPlan.timesOn(saturday, weekdays, weekend))
    assertEquals(weekdays, ReminderPlan.timesOn(saturday, weekdays, null))
    val zone = ZoneId.of("Europe/Rome")
    val timesFor = { day: LocalDate -> ReminderPlan.timesOn(day, weekdays, weekend) }
    // Friday 22:00: slot 2 (21:30) has no weekend counterpart, so it's next on Monday.
    val fridayNight = ZonedDateTime.of(LocalDate.of(2026, 10, 9), LocalTime.of(22, 0), zone)
    assertEquals(ZonedDateTime.of(LocalDate.of(2026, 10, 12), LocalTime.of(21, 30), zone), ReminderPlan.nextSlotTrigger(2, fridayNight, timesFor))
    assertEquals(ZonedDateTime.of(saturday, LocalTime.of(10, 0), zone), ReminderPlan.nextSlotTrigger(0, fridayNight, timesFor))
    assertNull(ReminderPlan.nextSlotTrigger(5, fridayNight, timesFor))
  }

  @Test
  fun theTopicIsHeldUntilStudyComesBack() {
    val start = LocalDate.of(2026, 10, 3)
    val periods = listOf(TimeOffPeriod(start = start, end = LocalDate.of(2026, 10, 10)))
    assertEquals(start, TimeOff.heldSince(periods, LocalDate.of(2026, 10, 5), setOf(start.minusDays(1))))
    assertEquals("still held on return", start, TimeOff.heldSince(periods, LocalDate.of(2026, 10, 11), setOf(start.minusDays(1))))
    assertNull("studied since", TimeOff.heldSince(periods, LocalDate.of(2026, 10, 12), setOf(LocalDate.of(2026, 10, 11))))
    assertNull("no time off", TimeOff.heldSince(emptyList(), today, emptySet()))
  }

  @Test
  fun aNewPeriodTakesOnlyItsOwnDaysFromOthers() {
    fun day(d: Int, m: Int = 9) = LocalDate.of(2026, m, d)
    val later = TimeOffPeriod(1, day(20), day(25))
    assertEquals("an earlier period leaves a later one alone", listOf(later), TimeOff.outside(later, day(10), day(15)))
    assertEquals("covered entirely", emptyList<TimeOffPeriod>(), TimeOff.outside(later, day(10), day(26)))
    val long = TimeOffPeriod(1, day(1), day(25))
    assertEquals("inside: split in two", listOf(TimeOffPeriod(1, day(1), day(9)), TimeOffPeriod(0, day(13), day(25))), TimeOff.outside(long, day(10), day(12)))
    assertEquals("an open new one keeps the days before", listOf(TimeOffPeriod(1, day(1), day(9))), TimeOff.outside(long, day(10), null))
    assertEquals("overlapping the start keeps the days after", listOf(TimeOffPeriod(1, day(16), day(25))), TimeOff.outside(TimeOffPeriod(1, day(12), day(25)), day(10), day(15)))
    assertEquals("an open one stays open", listOf(TimeOffPeriod(1, day(4, 10), null)), TimeOff.outside(TimeOffPeriod(1, day(1, 10), null), day(10), day(3, 10)))
  }

  @Test
  fun daysOffDontUseTheFreezeForYesterday() {
    // Studied up to Sunday, off Monday and Tuesday, nothing on Wednesday; today is Thursday.
    val records = (0L..3L).map { session(LocalDate.of(2026, 10, 4).minusDays(it)) }
    val off = TimeOff.days(listOf(TimeOffPeriod(start = LocalDate.of(2026, 10, 5), end = LocalDate.of(2026, 10, 6))), today)
    val summary = HabitSummaries.build(HabitKind.SIMPLE, records, today, off) { error("") }
    assertTrue(summary.yesterdayEmpty)
    assertTrue("the week's freeze is still free", summary.canFreezeYesterday)
    assertTrue(Freezes.canFreeze(today.minusDays(1), records.map { it.day }.toSet(), emptySet()))
  }
}
