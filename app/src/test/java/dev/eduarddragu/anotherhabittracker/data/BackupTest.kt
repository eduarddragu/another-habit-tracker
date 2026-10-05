package dev.eduarddragu.anotherhabittracker.data

import dev.eduarddragu.anotherhabittracker.domain.BackupException
import dev.eduarddragu.anotherhabittracker.domain.Backups
import dev.eduarddragu.anotherhabittracker.domain.EntryType
import dev.eduarddragu.anotherhabittracker.domain.HabitKind
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
  private val habits =
    listOf(
      Habit(id = 1, name = "Study", kind = HabitKind.STUDY, reminderTimes = "09:30,21:30", position = 0, icon = "BOOK"),
      Habit(id = 2, name = "Meditation", kind = HabitKind.SIMPLE, reminderTimes = "08:00", linkedPackage = "meditofoundation.medito", position = 1, weekendReminderTimes = "10:00,16:00", sessionMinutes = 20),
    )
  private val entries =
    listOf(
      Entry(id = 10, habitId = 1, day = LocalDate.of(2026, 9, 28), score = 4, minutes = 30, note = "select · timeouts, naïvely", topicId = "go-concurrency", loggedAt = 1_000L),
      Entry(id = 11, habitId = 1, day = LocalDate.of(2026, 9, 27), type = EntryType.KNOWN, topicId = "a-topic-retired-since", loggedAt = 2_000L),
      Entry(id = 12, habitId = 2, day = LocalDate.of(2026, 9, 28), type = EntryType.FREEZE, loggedAt = 3_000L),
      Entry(id = 13, habitId = 2, day = LocalDate.of(2026, 9, 28), type = EntryType.FINISHED, track = "Atomic Habits", module = "James Clear", loggedAt = 4_000L),
    )
  private val now = ZonedDateTime.of(2026, 9, 28, 23, 30, 0, 0, ZoneId.of("Europe/Rome"))
  private val timeOff = listOf(TimeOffRow(id = 1, start = LocalDate.of(2026, 9, 20), end = LocalDate.of(2026, 9, 22)), TimeOffRow(id = 2, start = LocalDate.of(2026, 9, 27)))
  private val good = Backups.encode(backupOf(habits, entries, now, appVersionCode = 21, curriculumVersion = 4, timeOff = timeOff))
  private val today = LocalDate.of(2026, 9, 28)

  private fun decode(text: String) = Backups.decode(text, today)

  private fun withTimeOff(vararg periods: TimeOffRow) = Backups.encode(backupOf(habits, entries, now, 21, timeOff = periods.toList()))

  @Test
  fun roundTripPreservesEveryField() {
    val file = decode(good)
    assertEquals(habits, file.habits.map { it.toHabit() })
    assertEquals(entries, file.entries.map { it.toEntry() })
    assertEquals(timeOff, file.timeOff.map { it.toRow() })
  }

  @Test
  fun exportedAtCarriesTheLocalTimeAndZone() {
    val file = decode(good)
    assertEquals("2026-09-28T23:30+02:00", file.exportedAt)
    assertEquals("Europe/Rome", file.zone)
    assertEquals(DB_SCHEMA, file.dbSchema)
  }

  @Test
  fun defaultsAreWrittenExplicitly() {
    assertTrue(good.contains("\"extraTopics\": \"\""))
    assertTrue(good.contains("\"score\": null"))
  }

  @Test
  fun unknownTopicIdsAreKept() = assertEquals("a-topic-retired-since", decode(good).entries[1].topicId)

  @Test
  fun summaryCountsSessionsOnly() = assertEquals("2 habits and 1 session", Backups.summary(decode(good)))

  @Test
  fun curriculumVersionIsWritten() = assertEquals(4, decode(good).curriculumVersion)

  @Test
  fun goldenV1FileStillImports() {
    val text = javaClass.classLoader!!.getResource("backup-v1.json")!!.readText()
    val file = decode(text)
    assertEquals(2, file.habits.size)
    assertEquals(3, file.entries.size)
    assertEquals(null, file.curriculumVersion)
  }

  @Test
  fun rejectsFilesItCannotUse() {
    val bad =
      mapOf(
        "not json" to "not json at all",
        "missing format" to good.replace("\"format\": \"another-habit-tracker\",", ""),
        "other app" to good.replace("\"another-habit-tracker\"", "\"some-other-app\""),
        "newer version" to good.replace("\"version\": ${Backups.VERSION}", "\"version\": 99"),
        "version zero" to good.replace("\"version\": ${Backups.VERSION}", "\"version\": 0"),
        "no habits" to Backups.encode(backupOf(emptyList(), emptyList(), now, 21)),
        "duplicate habit ids" to good.replace("\"id\": 2,", "\"id\": 1,"),
        "duplicate entry ids" to good.replace("\"id\": 12,", "\"id\": 10,"),
        "orphan session" to good.replace("\"habitId\": 2", "\"habitId\": 7"),
        "unknown kind" to good.replace("\"STUDY\"", "\"JUGGLING\""),
        "zero session length" to good.replace("\"sessionMinutes\": 20", "\"sessionMinutes\": 0"),
        "session length over a day" to good.replace("\"sessionMinutes\": 20", "\"sessionMinutes\": 1441"),
        "unknown type" to good.replace("\"FREEZE\"", "\"TELEPORT\""),
        "bad date" to good.replace("\"day\": \"2026-09-27\"", "\"day\": \"yesterday\""),
        "score out of range" to good.replace("\"score\": 4", "\"score\": 9"),
        "negative minutes" to good.replace("\"minutes\": 30", "\"minutes\": -5"),
        "longer than a day" to good.replace("\"minutes\": 30", "\"minutes\": 1441"),
        "habit id zero" to Backups.encode(backupOf(listOf(habits[0].copy(id = 0)), emptyList(), now, 21)),
        "session before 2000" to good.replace("\"day\": \"2026-09-27\"", "\"day\": \"1999-12-31\""),
        "session after tomorrow" to good.replace("\"day\": \"2026-09-27\"", "\"day\": \"2026-09-30\""),
        "session at the end of time" to good.replace("\"day\": \"2026-09-27\"", "\"day\": \"+999999999-12-31\""),
        "time off ends before it starts" to good.replace("\"end\": \"2026-09-22\"", "\"end\": \"2026-09-19\""),
        "unreadable time off start" to good.replace("\"start\": \"2026-09-20\"", "\"start\": \"soon\""),
        "unreadable time off end" to good.replace("\"end\": \"2026-09-22\"", "\"end\": \"later\""),
        "time off from the start of time" to good.replace("\"start\": \"2026-09-20\"", "\"start\": \"-999999999-01-01\""),
        "time off to the end of time" to good.replace("\"end\": \"2026-09-22\"", "\"end\": \"+999999999-12-31\""),
        "time off too far ahead" to withTimeOff(TimeOffRow(id = 1, start = today, end = today.plusYears(2).plusDays(1))),
        "duplicate time off ids" to withTimeOff(TimeOffRow(id = 1, start = LocalDate.of(2026, 9, 1), end = LocalDate.of(2026, 9, 2)), TimeOffRow(id = 1, start = LocalDate.of(2026, 9, 10))),
        "overlapping time off" to withTimeOff(TimeOffRow(id = 1, start = LocalDate.of(2026, 9, 1), end = LocalDate.of(2026, 9, 10)), TimeOffRow(id = 2, start = LocalDate.of(2026, 9, 10))),
        "time off after an open one" to withTimeOff(TimeOffRow(id = 2, start = LocalDate.of(2026, 9, 20)), TimeOffRow(id = 1, start = LocalDate.of(2026, 9, 1))),
      )
    bad.forEach { (case, text) -> assertThrows(case, BackupException::class.java) { decode(text) } }
  }

  @Test
  fun acceptsDatesAtTheEdges() {
    val tomorrow = good.replace("\"day\": \"2026-09-27\"", "\"day\": \"2026-09-29\"")
    assertEquals("2026-09-29", decode(tomorrow).entries[1].day)
    val earliest = good.replace("\"day\": \"2026-09-27\"", "\"day\": \"2000-01-01\"")
    assertEquals("2000-01-01", decode(earliest).entries[1].day)
    // Planned a year ahead, and right next to another period without touching it.
    val planned = withTimeOff(TimeOffRow(id = 1, start = LocalDate.of(2026, 9, 1), end = LocalDate.of(2026, 9, 9)), TimeOffRow(id = 2, start = LocalDate.of(2026, 9, 10), end = today.plusYears(1)))
    assertEquals(2, decode(planned).timeOff.size)
    assertEquals(1440, decode(good.replace("\"minutes\": 30", "\"minutes\": 1440")).entries[0].minutes)
  }

  @Test
  fun goldenV3FileStillImports() {
    val text = javaClass.classLoader!!.getResource("backup-v3.json")!!.readText()
    val file = decode(text)
    assertEquals(3, file.version)
    assertEquals(4, file.curriculumVersion)
    assertEquals("10:00,16:00", file.habits[1].weekendReminderTimes)
    assertEquals(3, file.entries.size)
    assertEquals(listOf(TimeOffRow(1, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 22)), TimeOffRow(2, LocalDate.of(2026, 9, 27), null)), file.timeOff.map { it.toRow() })
  }
}
