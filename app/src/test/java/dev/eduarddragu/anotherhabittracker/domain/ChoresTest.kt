package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChoresTest {
  private val day = LocalDate.of(2026, 10, 6)

  @Test
  fun everyCheckLeadsOnceBeforeAnyRepeats() {
    val leads = (0L until Chores.checks.size.toLong()).map { Chores.day(day.plusDays(it)).lead }
    assertEquals(Chores.checks.toSet(), leads.toSet())
  }

  @Test
  fun aDayListsEveryCheckStartingWithItsLead() {
    val today = Chores.day(day)
    assertEquals(today.lead, today.checks.first())
    assertEquals(Chores.checks.toSet(), today.checks.toSet())
    assertEquals(today, Chores.day(day))
  }

  @Test
  fun remindersAskAboutDifferentChecks() {
    val today = Chores.day(day)
    assertEquals("Start with this: ${today.lead.title.lowercase()}.", Chores.reminderLine(day, 0))
    assertTrue(Chores.reminderLine(day, 1) in today.checks[1].nudges)
    assertTrue(Chores.reminderLine(day, 2) in today.checks[2].nudges)
    assertEquals(Chores.reminderLine(day, 1), Chores.reminderLine(day, 1))
  }

  @Test
  fun lastCallStillTalksAboutTheStreak() {
    val text = ReminderMessages.text("Chores", HabitKind.SIMPLE, Tone.LAST_CALL, 4, day, 2, nudgeBody = Chores.reminderLine(day, 2))
    assertTrue(text.body.contains("4"))
    assertEquals(Chores.reminderLine(day, 1), ReminderMessages.text("Chores", HabitKind.SIMPLE, Tone.PUSH, 4, day, 1, nudgeBody = Chores.reminderLine(day, 1)).body)
  }

  @Test
  fun copyIsPlain() {
    Chores.checks.forEach { check -> (check.nudges + check.title + check.check).forEach { assertFalse(it, Char(0x2014) in it) } }
    Chores.mottos.forEach { assertFalse(it, Char(0x2014) in it) }
  }

  @Test
  fun everyMottoComesRoundBeforeAnyRepeats() {
    val mottos = (0L until Chores.mottos.size.toLong()).map { Chores.day(day.plusDays(it)).motto }
    assertEquals(Chores.mottos.toSet(), mottos.toSet())
  }

  @Test
  fun appliesToChoresOnly() {
    assertTrue(Chores.appliesTo(HabitKind.SIMPLE, "Chores", null))
    assertTrue(Chores.appliesTo(HabitKind.SIMPLE, "Home", HabitIcon.HOUSE.name))
    assertFalse(Chores.appliesTo(HabitKind.SIMPLE, "Reading", HabitIcon.BOOKMARK.name))
    assertFalse(Chores.appliesTo(HabitKind.STUDY, "Chores", null))
    assertFalse(Books.appliesTo(HabitKind.SIMPLE, "Chores", HabitIcon.HOUSE.name))
    assertFalse(Practices.appliesTo("Chores", null))
  }
}
