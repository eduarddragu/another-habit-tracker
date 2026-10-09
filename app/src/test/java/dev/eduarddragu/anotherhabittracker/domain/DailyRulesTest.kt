package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyRulesTest {
  private val today = LocalDate.of(2026, 10, 1) // a Thursday: Monday Sep 28 is in the same ISO week

  @Test
  fun yesterdayCanBeSavedOnlyWhenThereIsAStreakAndAFreeze() {
    val before = today.minusDays(2)
    assertTrue(Freezes.canSaveYesterday(today, setOf(before), emptySet()))
    assertFalse("nothing to save", Freezes.canSaveYesterday(today, emptySet(), emptySet()))
    assertFalse("already logged", Freezes.canSaveYesterday(today, setOf(before, today.minusDays(1)), emptySet()))
    assertFalse("freeze used this week", Freezes.canSaveYesterday(today, setOf(before), setOf(today.minusDays(3))))
    val longAgo = before.minusDays(10)
    assertTrue("days off with a session behind them", Freezes.canSaveYesterday(today, setOf(longAgo), emptySet(), bridges = (0L..9L).map { before.minusDays(it) }.toSet()))
    assertFalse("days off with no session behind them", Freezes.canSaveYesterday(today, emptySet(), emptySet(), bridges = setOf(before)))
    assertFalse("days off after a broken streak", Freezes.canSaveYesterday(today, setOf(longAgo), emptySet(), bridges = setOf(before)))
  }

  @Test
  fun oneMissedDayAfterAStreakIsASlip() {
    val streak = (2L..5L).map { today.minusDays(it) }.toSet()
    assertTrue(Freezes.slipped(today, streak, emptySet()))
    assertFalse("yesterday done", Freezes.slipped(today, streak + today.minusDays(1), emptySet()))
    assertFalse("yesterday frozen or off", Freezes.slipped(today, streak, setOf(today.minusDays(1))))
    assertFalse("two days missed", Freezes.slipped(today, (3L..5L).map { today.minusDays(it) }.toSet(), emptySet()))
    assertFalse("no streak at all", Freezes.slipped(today, emptySet(), emptySet()))
    val summary = HabitSummaries.build(HabitKind.SIMPLE, streak.map { LogRecord(it, EntryType.SESSION) }, today) { error("not study") }
    assertTrue(summary.slipped)
  }

  @Test
  fun aFreezeInsideTimeOffIsADayOff() {
    val tuesday = today.minusDays(2)
    val records = listOf(LogRecord(today.minusDays(3), EntryType.SESSION), LogRecord(tuesday, EntryType.FREEZE))
    val summary = HabitSummaries.build(HabitKind.SIMPLE, records, today, paused = setOf(tuesday)) { error("not study") }
    assertTrue("the week's freeze is free again", summary.canFreezeToday)
    assertTrue(tuesday in summary.daysOff)
    assertEquals(emptySet<LocalDate>(), Freezes.counted(setOf(tuesday, today), setOf(today), setOf(tuesday)))
  }

  @Test
  fun scoreMeaningsFollowThePickerRules() {
    assertEquals("Partially. Back in 4 days, unlocks nothing.", TopicPicker.scoreMeaning(2))
    assertEquals("Could explain the gist. Unlocks what's next, review in 14 days.", TopicPicker.scoreMeaning(3))
    assertEquals("Could teach it. Review in 90 days.", TopicPicker.scoreMeaning(5))
  }

  @Test
  fun yesterdayCanBeLoggedWheneverItIsEmpty() {
    val today = LocalDate.of(2026, 10, 1)
    val yesterday = today.minusDays(1)
    val before = yesterday.minusDays(1)
    assertTrue(Freezes.yesterdayEmpty(today, setOf(before), emptySet()))
    assertTrue("no streak to save, still loggable", Freezes.yesterdayEmpty(today, setOf(before.minusDays(3)), emptySet()))
    assertTrue("today already done", Freezes.yesterdayEmpty(today, setOf(before, today), emptySet()))
    assertFalse("logged", Freezes.yesterdayEmpty(today, setOf(before, yesterday), emptySet()))
    assertFalse("frozen", Freezes.yesterdayEmpty(today, setOf(before), setOf(yesterday)))
    assertFalse("habit started today", Freezes.yesterdayEmpty(today, setOf(today), emptySet()))
    assertTrue("a day off yesterday is empty", Freezes.yesterdayEmpty(today, setOf(before), emptySet()))
    assertTrue("a freeze shows the habit was running", Freezes.yesterdayEmpty(today, emptySet(), setOf(before)))
    assertFalse("never logged: a habit just added", Freezes.yesterdayEmpty(today, emptySet(), emptySet()))
  }

  @Test
  fun onItQuietsEverythingButTheLastCall() {
    val until = 10_000L
    assertTrue(ReminderPlan.quieted(Tone.PUSH, 5_000L, until))
    assertFalse(ReminderPlan.quieted(Tone.LAST_CALL, 5_000L, until))
    assertFalse(ReminderPlan.quieted(Tone.PUSH, 12_000L, until))
    assertFalse(ReminderPlan.quieted(Tone.NUDGE, 5_000L, null))
  }
}
