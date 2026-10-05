package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BooksTest {
  private val today = LocalDate.of(2026, 10, 7)

  private fun read(daysAgo: Long, title: String?, minutes: Int = 20, author: String? = null) = LogRecord(today.minusDays(daysAgo), EntryType.SESSION, minutes = minutes, track = title, module = author)

  private fun finished(daysAgo: Long, title: String, author: String? = null) = LogRecord(today.minusDays(daysAgo), EntryType.FINISHED, track = title, module = author)

  @Test
  fun appliesToReadingHabitsOnly() {
    assertTrue(Books.appliesTo(HabitKind.SIMPLE, "Reading", HabitIcon.BOOKMARK.name))
    assertTrue(Books.appliesTo(HabitKind.SIMPLE, "Read before bed", null))
    assertTrue(Books.appliesTo(HabitKind.SIMPLE, "Novels", HabitIcon.BOOKMARK.name))
    assertFalse(Books.appliesTo(HabitKind.SIMPLE, "Meditation", HabitIcon.LOTUS.name))
    // Study has its own course or book field.
    assertFalse(Books.appliesTo(HabitKind.STUDY, "Reading papers", null))
  }

  @Test
  fun theCurrentBookAddsUpItsSessions() {
    val shelf = Books.shelf(listOf(read(3, "Atomic Habits", 20, "James Clear"), read(2, null, 10), read(1, "atomic  habits ", 30)))
    val current = shelf.current!!
    assertEquals("atomic  habits", current.title)
    assertEquals("an author given once stays", "James Clear", current.author)
    assertEquals(today.minusDays(3), current.started)
    assertEquals(today.minusDays(1), current.lastRead)
    assertEquals(2, current.sessions)
    assertEquals(50, current.minutes)
    assertTrue(shelf.finished.isEmpty())
  }

  @Test
  fun aFinishedBookMovesToTheListNewestFirst() {
    val shelf =
      Books.shelf(
        listOf(
          read(10, "Dune", 30, "Frank Herbert"),
          finished(9, "Dune", "Frank Herbert"),
          read(8, "Atomic Habits"),
          // The finishing session is logged after the mark on the same day: it still counts for that read.
          finished(5, "Atomic Habits"),
          read(5, "Atomic Habits", 15),
        )
      )
    assertNull(shelf.current)
    assertEquals(listOf("Atomic Habits", "Dune"), shelf.finished.map { it.title })
    assertEquals(today.minusDays(5), shelf.finished[0].finished)
    assertEquals(2, shelf.finished[0].sessions)
    assertEquals(35, shelf.finished[0].minutes)
    assertEquals("Frank Herbert", shelf.finished[1].author)
  }

  @Test
  fun aSessionAfterTheFinishStartsARead() {
    val shelf = Books.shelf(listOf(read(5, "Dune"), finished(4, "Dune"), read(1, "Dune")))
    assertEquals(1, shelf.current!!.sessions)
    assertEquals(1, shelf.finished.size)
  }

  @Test
  fun theMostRecentlyReadOpenBookIsCurrent() {
    val shelf = Books.shelf(listOf(read(5, "Dune"), read(3, "Atomic Habits"), read(2, "Dune")))
    assertEquals("Dune", shelf.current!!.title)
  }

  @Test
  fun aBookMarkedTwiceOnOneDayIsListedOnce() {
    val shelf = Books.shelf(listOf(read(2, "Dune"), finished(2, "Dune"), finished(2, "Dune")))
    assertEquals(1, shelf.finished.size)
  }

  @Test
  fun eachSituationGetsItsLine() {
    fun situation(vararg records: LogRecord, done: Boolean = false) = Books.situation(Books.shelf(records.toList()), today, done)
    assertEquals(BookSituation.NO_BOOK, situation())
    assertEquals(BookSituation.READ_TODAY_UNNAMED, situation(read(0, null), done = true))
    assertEquals(BookSituation.READ_YESTERDAY, situation(read(1, "Dune")))
    assertEquals(BookSituation.AWAY, situation(read(3, "Dune")))
    assertEquals(BookSituation.READ_TODAY, situation(read(0, "Dune"), done = true))
    assertEquals(BookSituation.JUST_FINISHED, situation(read(1, "Dune"), finished(1, "Dune")))
    assertEquals(BookSituation.JUST_FINISHED, situation(read(0, "Dune"), finished(0, "Dune"), done = true))
    // A new book started after the finish takes over.
    assertEquals(BookSituation.READ_TODAY, situation(finished(1, "Dune"), read(0, "Atomic Habits"), done = true))
    // A finish from three days ago is old news.
    assertEquals(BookSituation.NO_BOOK, situation(read(3, "Dune"), finished(3, "Dune")))
  }

  @Test
  fun theLineNamesTheBookTheMinutesAndTheDays() {
    val yesterday = Books.day(Books.shelf(listOf(read(1, "Atomic Habits", author = "James Clear"))), today, doneToday = false, sessionMinutes = 25)
    assertEquals("Atomic Habits", yesterday.title)
    assertEquals("James Clear", yesterday.author)
    assertTrue(yesterday.line, "Atomic Habits" in yesterday.line || "25 minutes" in yesterday.line || "page" in yesterday.line)
    val away = Books.day(Books.shelf(listOf(read(4, "Dune"))), today, false, 20)
    assertFalse(away.line, '{' in away.line)
    val finished = Books.day(Books.shelf(listOf(read(1, "Dune"), finished(0, "Dune"))), today, false, 20)
    assertEquals("Dune", finished.title)
    val none = Books.day(Books.shelf(emptyList()), today, false, 20)
    assertNull(none.title)
  }

  @Test
  fun everyLineFillsItsPlaceholders() {
    val shelf = Books.shelf(listOf(read(4, "Dune")))
    (0L until 60L).forEach { offset ->
      val day = Books.day(shelf, today.plusDays(offset), false, 1)
      assertFalse(day.line, '{' in day.line)
      assertFalse(day.line, "1 minutes" in day.line)
    }
  }

  @Test
  fun theLineStaysTheSameAllDayAndChangesAcrossDays() {
    val shelf = Books.shelf(listOf(read(1, "Dune")))
    assertEquals(Books.day(shelf, today, false, 20), Books.day(shelf, today, false, 20))
    val lines = (0L until 30L).map { Books.day(Books.shelf(listOf(LogRecord(today.plusDays(it - 1), EntryType.SESSION, track = "Dune"))), today.plusDays(it), false, 20).line }.toSet()
    assertTrue("the yesterday pool rotates", lines.size > 1)
  }

  @Test
  fun copyIsPlain() {
    Books.lines.values.flatten().forEach { line ->
      assertFalse(line, Char(0x2014) in line || Char(0x2013) in line)
      assertTrue(line, line.all { it.code < 128 })
    }
    BookSituation.entries.forEach { assertTrue("$it has variants", Books.lines.getValue(it).size >= 2) }
  }

  @Test
  fun aFinishedMarkIsNeverASession() {
    val records = listOf(read(1, "Dune", 30), finished(0, "Dune"))
    val summary = HabitSummaries.build(HabitKind.SIMPLE, records, today) { error("not study") }
    assertFalse("finishing a book doesn't do today", summary.doneToday)
    assertTrue(summary.dayOpen)
    assertEquals(1, summary.stats.streak)
    assertEquals(1, summary.stats.totalSessions)
    assertEquals(30, summary.stats.totalMinutes)
    assertEquals(setOf(today.minusDays(1)), summary.cells.keys)
    assertTrue(summary.canFreezeToday)
    val recap = WeeklyRecap.build(listOf(RecapHabit("Reading", HabitKind.SIMPLE, records, streak = 1)), today) { null }
    assertTrue(recap.body, recap.body.startsWith("Reading: 1 of"))
    assertTrue(recap.body, ", 30 min." in recap.body)
  }
}
