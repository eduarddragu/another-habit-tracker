package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticesTest {
  private val day = LocalDate.of(2026, 10, 1)

  @Test
  fun everyPracticeComesRoundOnceBeforeAnyRepeats() {
    val cycle = (0L until Practices.all.size.toLong()).map { Practices.forDay(day.plusDays(it)) }
    assertEquals(Practices.all.toSet(), cycle.toSet())
  }

  @Test
  fun practicesAreShortAndPlain() {
    Practices.all.forEach { practice ->
      assertEquals(practice.title, 3, practice.steps.size)
      (practice.steps + practice.title).forEach { assertFalse(it, Char(0x2014) in it) }
    }
  }

  @Test
  fun appliesToMeditationOnly() {
    assertTrue(Practices.appliesTo("Meditation", null))
    assertTrue(Practices.appliesTo("Evening sit", "meditofoundation.medito"))
    assertFalse(Practices.appliesTo("Study", null))
  }
}
