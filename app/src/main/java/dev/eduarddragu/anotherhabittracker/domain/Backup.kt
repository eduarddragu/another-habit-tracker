package dev.eduarddragu.anotherhabittracker.domain

import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Everything the app knows, as one JSON document: the manual export, the nightly copy and the input of
 * the weekly review all use it. Deliberately separate from the Room entities, so the database can
 * change without breaking old files.
 *
 * Dates: `day` is the phone's local day a session counts for (ISO date). `exportedAt` is the local
 * time of the export with its offset, and `zone` the phone's timezone then, so a reader elsewhere can
 * tell which day was still open. `loggedAt` is epoch milliseconds. `curriculumVersion` says which
 * curriculum the topic ids refer to (absent in version 1 files). `appVersionCode` and `dbSchema` are
 * for diagnostics only.
 */
@Serializable
data class BackupFile(
  val format: String,
  val version: Int,
  val exportedAt: String,
  val zone: String,
  val appVersionCode: Long,
  val dbSchema: Int,
  /** Added in version 2. */
  val curriculumVersion: Int? = null,
  val habits: List<BackupHabit>,
  val entries: List<BackupEntry>,
  /** Added in version 3. */
  val timeOff: List<BackupTimeOff> = emptyList(),
)

/** A stretch of days off: ISO dates, [end] null while open. */
@Serializable
data class BackupTimeOff(val id: Long, val start: String, val end: String?)

@Serializable
data class BackupHabit(
  val id: Long,
  val name: String,
  val kind: String,
  val reminderTimes: String,
  val linkedPackage: String?,
  val position: Int,
  val icon: String?,
  /** Added in version 3; null means the same times as weekdays. */
  val weekendReminderTimes: String? = null,
  /** Added in version 4; null means the kind's default length. */
  val sessionMinutes: Int? = null,
)

@Serializable
data class BackupEntry(
  val id: Long,
  val habitId: Long,
  val day: String,
  val type: String,
  val score: Int?,
  val minutes: Int?,
  val note: String,
  val extraTopics: String,
  val track: String?,
  val module: String?,
  val loggedAt: Long,
  /** Kept even when the curriculum no longer has it: retired topics must survive a restore. */
  val topicId: String?,
)

/** A file that can't be imported, with a message that says why in plain words. */
class BackupException(message: String) : Exception(message)

object Backups {
  const val FORMAT = "another-habit-tracker"

  /**
   * Bump for every change to the fields, added ones included: an older build must refuse a newer file
   * rather than drop what it doesn't know. Older files are brought up to date in [decode].
   */
  const val VERSION = 4

  /** The name of the nightly file. */
  const val NIGHTLY_NAME = "$FORMAT.json"

  private val json = Json {
    prettyPrint = true
    encodeDefaults = true
  }

  fun create(habits: List<BackupHabit>, entries: List<BackupEntry>, now: OffsetDateTime, zone: String, appVersionCode: Long, dbSchema: Int, curriculumVersion: Int?, timeOff: List<BackupTimeOff> = emptyList()) =
    BackupFile(FORMAT, VERSION, now.toString(), zone, appVersionCode, dbSchema, curriculumVersion, habits, entries, timeOff)

  fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

  /** No date in a file may come before this one. */
  val EARLIEST_DAY: LocalDate = LocalDate.of(2000, 1, 1)

  /** A session can't be longer than a day. */
  const val MAX_MINUTES = 24 * 60

  /**
   * Parses and checks a file before anything is replaced. Throws [BackupException] when it can't be
   * used. Session days must fall between [EARLIEST_DAY] and the day after [today] (a file written in
   * a zone ahead of this one may already be on tomorrow), time off within two years of it: a date far
   * out of range would make every launch walk millions of days, or overflow.
   */
  fun decode(text: String, today: LocalDate): BackupFile {
    val file =
      try {
        json.decodeFromString(BackupFile.serializer(), text)
      } catch (error: SerializationException) {
        throw BackupException("This isn't a habit tracker backup, or it's damaged.")
      } catch (error: IllegalArgumentException) {
        throw BackupException("This isn't a habit tracker backup, or it's damaged.")
      }
    check(file.format == FORMAT) { "This file comes from another app." }
    check(file.version <= VERSION) { "This backup comes from a newer version of the app. Update first." }
    check(file.version >= 1) { "This backup has an unknown version." }
    check(file.habits.isNotEmpty()) { "This backup has no habits in it." }
    val habitIds = file.habits.map { it.id }
    check(habitIds.toSet().size == habitIds.size) { "Two habits in this file share an id." }
    // Room hands out ids from 1: a habit with 0 would be given a new id and lose its sessions.
    check(habitIds.all { it > 0 }) { "A habit in this file has an invalid id." }
    check(file.entries.map { it.id }.toSet().size == file.entries.size) { "Two sessions in this file share an id." }
    check(file.habits.all { runCatching { HabitKind.valueOf(it.kind) }.isSuccess }) { "A habit in this file has an unknown kind." }
    check(file.habits.all { it.sessionMinutes == null || it.sessionMinutes in 1..MAX_MINUTES }) { "A habit in this file has a session length outside a day." }
    fun inRange(day: LocalDate, latest: LocalDate = today.plusDays(1)) = !day.isBefore(EARLIEST_DAY) && !day.isAfter(latest)
    // Time off can be planned up to a year ahead, and splitting a period around a new one can move
    // a start into the future too; a second year leaves room for a phone whose clock was behind.
    val latestTimeOff = today.plusYears(2)
    val known = habitIds.toSet()
    file.entries.forEach { entry ->
      check(entry.habitId in known) { "A session in this file belongs to a habit that isn't there." }
      check(runCatching { EntryType.valueOf(entry.type) }.isSuccess) { "A session in this file has an unknown type." }
      val day = runCatching { LocalDate.parse(entry.day) }.getOrNull() ?: throw BackupException("A session in this file has an unreadable date.")
      check(inRange(day)) { "A session in this file has a date before 2000 or in the future." }
      check(entry.score == null || entry.score in 1..5) { "A session in this file has a score outside 1 to 5." }
      check(entry.minutes == null || entry.minutes >= 0) { "A session in this file has negative minutes." }
      check(entry.minutes == null || entry.minutes <= MAX_MINUTES) { "A session in this file is longer than a day." }
    }
    check(file.timeOff.map { it.id }.toSet().size == file.timeOff.size) { "Two time off periods in this file share an id." }
    val periods =
      file.timeOff.map { period ->
        val start = runCatching { LocalDate.parse(period.start) }.getOrNull()
        val end = period.end?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (start == null || (period.end != null && end == null)) throw BackupException("A time off period in this file has an unreadable date.")
        check(inRange(start, latestTimeOff) && (end == null || inRange(end, latestTimeOff))) { "A time off period in this file has a date before 2000 or too far ahead." }
        check(end == null || !end.isBefore(start)) { "A time off period in this file ends before it starts." }
        start to end
      }
    // By start, each period must end before the next one begins; an open one runs on forever.
    periods.sortedBy { it.first }.zipWithNext().forEach { (earlier, later) ->
      val end = earlier.second
      check(end != null && end.isBefore(later.first)) { "Two time off periods in this file overlap." }
    }
    return file
  }

  /** "2 habits and 41 sessions", for the import confirmation. Known-topic marks and freezes aren't sessions. */
  fun summary(file: BackupFile): String {
    val sessions = file.entries.count { it.type == EntryType.SESSION.name }
    val habits = file.habits.size
    return "$habits ${if (habits == 1) "habit" else "habits"} and $sessions ${if (sessions == 1) "session" else "sessions"}"
  }

  /** The name offered when exporting a copy by hand. */
  fun fileName(day: LocalDate): String = "$FORMAT-$day.json"

  private inline fun check(ok: Boolean, message: () -> String) {
    if (!ok) throw BackupException(message())
  }
}
