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
      Habit(id = 2, name = "Meditation", kind = HabitKind.SIMPLE, reminderTimes = "08:00", linkedPackage = "meditofoundation.medito", position = 1),
    )
  private val entries =
    listOf(
      Entry(id = 10, habitId = 1, day = LocalDate.of(2026, 9, 28), score = 4, minutes = 30, note = "select · timeouts, naïvely", topicId = "go-concurrency", loggedAt = 1_000L),
      Entry(id = 11, habitId = 1, day = LocalDate.of(2026, 9, 27), type = EntryType.KNOWN, topicId = "a-topic-retired-since", loggedAt = 2_000L),
      Entry(id = 12, habitId = 2, day = LocalDate.of(2026, 9, 28), type = EntryType.FREEZE, loggedAt = 3_000L),
    )
  private val now = ZonedDateTime.of(2026, 9, 28, 23, 30, 0, 0, ZoneId.of("Europe/Rome"))
  private val good = Backups.encode(backupOf(habits, entries, now, appVersionCode = 21, curriculumVersion = 4))

  @Test
  fun roundTripPreservesEveryField() {
    val file = Backups.decode(good)
    assertEquals(habits, file.habits.map { it.toHabit() })
    assertEquals(entries, file.entries.map { it.toEntry() })
  }

  @Test
  fun exportedAtCarriesTheLocalTimeAndZone() {
    val file = Backups.decode(good)
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
  fun unknownTopicIdsAreKept() = assertEquals("a-topic-retired-since", Backups.decode(good).entries[1].topicId)

  @Test
  fun summaryCountsSessionsOnly() = assertEquals("2 habits and 1 session", Backups.summary(Backups.decode(good)))

  @Test
  fun curriculumVersionIsWritten() = assertEquals(4, Backups.decode(good).curriculumVersion)

  @Test
  fun goldenV1FileStillImports() {
    val text = javaClass.classLoader!!.getResource("backup-v1.json")!!.readText()
    val file = Backups.decode(text)
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
        "newer version" to good.replace("\"version\": 2", "\"version\": 99"),
        "version zero" to good.replace("\"version\": 2", "\"version\": 0"),
        "no habits" to Backups.encode(backupOf(emptyList(), emptyList(), now, 21)),
        "duplicate habit ids" to good.replace("\"id\": 2,", "\"id\": 1,"),
        "duplicate entry ids" to good.replace("\"id\": 12,", "\"id\": 10,"),
        "orphan session" to good.replace("\"habitId\": 2", "\"habitId\": 7"),
        "unknown kind" to good.replace("\"STUDY\"", "\"JUGGLING\""),
        "unknown type" to good.replace("\"FREEZE\"", "\"TELEPORT\""),
        "bad date" to good.replace("\"2026-09-27\"", "\"yesterday\""),
        "score out of range" to good.replace("\"score\": 4", "\"score\": 9"),
      )
    bad.forEach { (case, text) -> assertThrows(case, BackupException::class.java) { Backups.decode(text) } }
  }
}
