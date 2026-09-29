package dev.eduarddragu.anotherhabittracker.data

import dev.eduarddragu.anotherhabittracker.domain.BackupEntry
import dev.eduarddragu.anotherhabittracker.domain.BackupFile
import dev.eduarddragu.anotherhabittracker.domain.BackupHabit
import dev.eduarddragu.anotherhabittracker.domain.Backups
import dev.eduarddragu.anotherhabittracker.domain.EntryType
import dev.eduarddragu.anotherhabittracker.domain.HabitKind
import java.time.LocalDate
import java.time.ZonedDateTime

// Room entities to and from the backup document. Ids are kept, so a restore gives back the same
// habits (and the alarms that use their ids) and the same history.

fun Habit.toBackup() = BackupHabit(id, name, kind.name, reminderTimes, linkedPackage, position, icon)

fun Entry.toBackup() = BackupEntry(id, habitId, day.toString(), type.name, score, minutes, note, extraTopics, track, module, loggedAt, topicId)

fun BackupHabit.toHabit() = Habit(id, name, HabitKind.valueOf(kind), reminderTimes, linkedPackage, position, icon)

fun BackupEntry.toEntry() =
  Entry(id, habitId, LocalDate.parse(day), EntryType.valueOf(type), score, minutes, note, extraTopics, track, module, loggedAt, topicId)

/** Schema version of the Room database the file was written from (see AppDatabase). */
const val DB_SCHEMA = 3

fun backupOf(habits: List<Habit>, entries: List<Entry>, now: ZonedDateTime, appVersionCode: Long, curriculumVersion: Int? = null): BackupFile =
  Backups.create(habits.map { it.toBackup() }, entries.map { it.toBackup() }, now.toOffsetDateTime(), now.zone.id, appVersionCode, DB_SCHEMA, curriculumVersion)
