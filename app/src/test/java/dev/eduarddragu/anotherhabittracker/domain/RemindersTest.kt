package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {
  private val rome = ZoneId.of("Europe/Rome")

  private fun at(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), rome)

  @Test
  fun laterTodayWhenTheSlotIsStillAhead() =
    assertEquals(at("2026-10-10", "13:30"), ReminderPlan.nextTrigger(LocalTime.of(13, 30), at("2026-10-10", "09:00")))

  @Test
  fun tomorrowWhenTheSlotHasPassedOrIsNow() {
    assertEquals(at("2026-10-11", "08:00"), ReminderPlan.nextTrigger(LocalTime.of(8, 0), at("2026-10-10", "08:00")))
    assertEquals(at("2026-10-11", "08:00"), ReminderPlan.nextTrigger(LocalTime.of(8, 0), at("2026-10-10", "22:00")))
  }

  @Test
  fun keepsWallClockTimeAcrossDstChanges() {
    // 2026-10-25: clocks go back in Rome; 2026-03-29: clocks go forward.
    val autumn = ReminderPlan.nextTrigger(LocalTime.of(9, 30), at("2026-10-24", "22:00"))
    assertEquals(LocalTime.of(9, 30), autumn.toLocalTime())
    assertEquals("+01:00", autumn.offset.id)
    val spring = ReminderPlan.nextTrigger(LocalTime.of(9, 30), at("2026-03-28", "22:00"))
    assertEquals(LocalTime.of(9, 30), spring.toLocalTime())
    assertEquals("+02:00", spring.offset.id)
  }

  @Test
  fun slotInsideTheSpringGapMovesForward() {
    val gap = ReminderPlan.nextTrigger(LocalTime.of(2, 30), at("2026-03-28", "22:00"))
    assertEquals(LocalTime.of(3, 30), gap.toLocalTime())
  }

  @Test
  fun tonesEscalateThroughTheDay() {
    assertEquals(
      listOf(Tone.OPENING, Tone.NUDGE, Tone.PUSH, Tone.PUSH, Tone.LAST_CALL),
      (0 until 5).map { ReminderPlan.tone(it, 5) },
    )
    assertEquals(listOf(Tone.OPENING, Tone.NUDGE, Tone.PUSH, Tone.LAST_CALL), (0 until 4).map { ReminderPlan.tone(it, 4) })
    assertEquals(Tone.LAST_CALL, ReminderPlan.tone(0, 1))
  }

  @Test
  fun messagesAreStableForTheSameDayAndSlot() {
    val day = LocalDate.of(2026, 10, 10)
    val a = ReminderMessages.text("Study", HabitKind.STUDY, Tone.NUDGE, 3, day, 1)
    val b = ReminderMessages.text("Study", HabitKind.STUDY, Tone.NUDGE, 3, day, 1)
    assertEquals(a, b)
    assertTrue(a.body.contains("Study"))
  }

  @Test
  fun lastCallMentionsTheStreakOnlyWhenThereIsOne() {
    val day = LocalDate.of(2026, 10, 10)
    assertTrue(ReminderMessages.text("Meditation", HabitKind.SIMPLE, Tone.LAST_CALL, 12, day, 3).body.contains("12"))
    val noStreak = ReminderMessages.text("Meditation", HabitKind.SIMPLE, Tone.LAST_CALL, 0, day, 3)
    assertTrue(noStreak.title.endsWith("last call"))
    assertTrue(noStreak.body.contains("Meditation"))
  }

  @Test
  fun reminderTimesRoundTrip() {
    val times = parseReminderTimes("11:00, 08:00,bogus,11:00,22:00")
    assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(11, 0), LocalTime.of(22, 0)), times)
    assertEquals("08:00,11:00,22:00", formatReminderTimes(times))
  }

  @Test
  fun aOneDayStreakIsSingular() {
    val day = LocalDate.of(2026, 10, 1)
    for (slot in 0..40) {
      for (tone in Tone.entries) {
        val text = ReminderMessages.text("Study", HabitKind.STUDY, tone, 1, day.plusDays(slot.toLong()), slot)
        assertTrue(text.body, !text.body.contains("1 days"))
      }
    }
  }
}
