package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyRulesTest {
  private val today = LocalDate.of(2026, 10, 1) // a Thursday: Monday Sep 28 is in the same ISO week

  @Test
  fun freezeUsedThisWeekIsReported() {
    assertEquals(today.minusDays(1), Freezes.usedThisWeek(today, setOf(today.minusDays(1))))
    assertNull(Freezes.usedThisWeek(today, setOf(today.minusDays(7))))
  }

  @Test
  fun yesterdayCanBeSavedOnlyWhenThereIsAStreakAndAFreeze() {
    val before = today.minusDays(2)
    assertTrue(Freezes.canSaveYesterday(today, setOf(before), emptySet()))
    assertFalse("nothing to save", Freezes.canSaveYesterday(today, emptySet(), emptySet()))
    assertFalse("already logged", Freezes.canSaveYesterday(today, setOf(before, today.minusDays(1)), emptySet()))
    assertFalse("freeze used this week", Freezes.canSaveYesterday(today, setOf(before), setOf(today.minusDays(3))))
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
