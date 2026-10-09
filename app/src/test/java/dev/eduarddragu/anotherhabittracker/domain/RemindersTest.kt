package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {
  private val rome = ZoneId.of("Europe/Rome")

  private fun at(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), rome)

  private fun every(vararg times: String): (LocalDate) -> List<LocalTime> = { _ -> times.map(LocalTime::parse) }

  @Test
  fun laterTodayWhenTheSlotIsStillAhead() =
    assertEquals(at("2026-10-10", "13:30"), ReminderPlan.nextSlotTrigger(0, at("2026-10-10", "09:00"), every("13:30")))

  @Test
  fun tomorrowWhenTheSlotHasPassedOrIsNow() {
    assertEquals(at("2026-10-11", "08:00"), ReminderPlan.nextSlotTrigger(0, at("2026-10-10", "08:00"), every("08:00")))
    assertEquals(at("2026-10-11", "08:00"), ReminderPlan.nextSlotTrigger(0, at("2026-10-10", "22:00"), every("08:00")))
  }

  @Test
  fun keepsWallClockTimeAcrossDstChanges() {
    // 2026-10-25: clocks go back in Rome; 2027-03-28: clocks go forward.
    val autumn = ReminderPlan.nextSlotTrigger(0, at("2026-10-24", "22:00"), every("09:30"))!!
    assertEquals(ZonedDateTime.parse("2026-10-25T09:30+01:00[Europe/Rome]"), autumn)
    val spring = ReminderPlan.nextSlotTrigger(0, at("2027-03-27", "22:00"), every("09:30"))!!
    assertEquals(ZonedDateTime.parse("2027-03-28T09:30+02:00[Europe/Rome]"), spring)
  }

  @Test
  fun slotInsideTheSpringGapMovesForward() {
    val gap = ReminderPlan.nextSlotTrigger(0, at("2027-03-27", "22:00"), every("02:30"))!!
    assertEquals(ZonedDateTime.parse("2027-03-28T03:30+02:00[Europe/Rome]"), gap)
  }

  @Test
  fun slotInTheRepeatedHourFiresOnce() {
    // 02:30 happens twice on 2026-10-25. Once the first one has passed, the next is tomorrow's.
    val afterFirst = ZonedDateTime.parse("2026-10-25T02:31+02:00[Europe/Rome]")
    assertEquals(ZonedDateTime.parse("2026-10-26T02:30+01:00[Europe/Rome]"), ReminderPlan.nextSlotTrigger(0, afterFirst, every("02:30")))
  }

  @Test
  fun slotsSwitchListsBetweenWeekdaysAndWeekend() {
    val weekdays = listOf(LocalTime.of(8, 0), LocalTime.of(21, 0))
    val weekend = listOf(LocalTime.of(10, 0))
    val timesFor = { day: LocalDate -> ReminderPlan.timesOn(day, weekdays, weekend) }
    // Friday night to Saturday: slot 0 is the weekend's 10:00, slot 1 waits for Monday.
    val friday = at("2026-10-09", "21:30")
    assertEquals(at("2026-10-10", "10:00"), ReminderPlan.nextSlotTrigger(0, friday, timesFor))
    assertEquals(at("2026-10-12", "21:00"), ReminderPlan.nextSlotTrigger(1, friday, timesFor))
    // Sunday after its slot to Monday: back to the weekday list.
    val sunday = at("2026-10-11", "11:00")
    assertEquals(at("2026-10-12", "08:00"), ReminderPlan.nextSlotTrigger(0, sunday, timesFor))
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

  @Test
  fun midnightRefreshNeverSkipsADay() {
    // A process starting in the first 30 s of a day keeps today's refresh, which hasn't fired yet.
    assertEquals(at("2026-10-10", "00:00:30"), ReminderPlan.nextMidnight(at("2026-10-10", "00:00:10")))
    // The refresh itself, firing at 00:00:30, schedules tomorrow's.
    assertEquals(at("2026-10-11", "00:00:30"), ReminderPlan.nextMidnight(at("2026-10-10", "00:00:30")))
    assertEquals(at("2026-10-11", "00:00:30"), ReminderPlan.nextMidnight(at("2026-10-10", "15:00")))
  }

  @Test
  fun lateRemindersAreDropped() {
    val slot = at("2026-10-10", "21:45")
    assertFalse("on time", ReminderPlan.tooLate(slot, at("2026-10-10", "21:45:02")))
    assertFalse("within the hour", ReminderPlan.tooLate(slot, at("2026-10-10", "22:44")))
    assertTrue("more than an hour late", ReminderPlan.tooLate(at("2026-10-10", "13:30"), at("2026-10-10", "14:31")))
    assertTrue("last night's, after midnight", ReminderPlan.tooLate(slot, at("2026-10-11", "00:05")))
    assertFalse("no time carried", ReminderPlan.tooLate(null, at("2026-10-11", "00:05")))
    // After a timezone change the day is the phone's current one: 00:10 in Rome is still the 10th in London.
    assertFalse(ReminderPlan.tooLate(at("2026-10-10", "23:50"), at("2026-10-11", "00:10").withZoneSameInstant(ZoneId.of("Europe/London"))))
  }

  @Test
  fun theDayAfterASlipSaysSoAndMinutesFollowTheHabit() {
    val day = LocalDate.of(2026, 10, 7)
    val opening = ReminderMessages.text("Chores", HabitKind.SIMPLE, Tone.OPENING, 0, day, 0, slipped = true)
    assertTrue("No streak yet" !in opening.body)
    val last = ReminderMessages.text("Chores", HabitKind.SIMPLE, Tone.LAST_CALL, 0, day, 2, slipped = true)
    assertTrue(last.body.contains("Chores") && ("Missed yesterday" in last.body || "blip" in last.body))
    val nudges = (0L..40L).flatMap { d -> (1..2).map { ReminderMessages.text("Graphs", HabitKind.STUDY, Tone.NUDGE, 3, day.plusDays(d), it, minutes = 45).body } }
    assertTrue(nudges.none { "thirty" in it.lowercase() || "{" in it })
    assertTrue(nudges.any { "45 minutes" in it })
  }

  @Test
  fun threeDaysIsTheFirstMilestone() = assertTrue(Milestones.line(3)!!.isNotBlank())
}
