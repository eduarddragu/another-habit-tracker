package dev.eduarddragu.anotherhabittracker.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import dev.eduarddragu.anotherhabittracker.domain.EntryType
import dev.eduarddragu.anotherhabittracker.domain.HabitIcon
import dev.eduarddragu.anotherhabittracker.domain.HabitKind
import dev.eduarddragu.anotherhabittracker.domain.parseReminderTimes
import dev.eduarddragu.anotherhabittracker.domain.ReminderPlan
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "habits")
data class Habit(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val kind: HabitKind,
  /** Comma-separated "HH:mm" local times, see parseReminderTimes. */
  val reminderTimes: String,
  /** Package of an app the reminder can open (e.g. a meditation app), if any. */
  val linkedPackage: String? = null,
  val position: Int = 0,
  /** A HabitIcon name; null means the default for the habit's kind. Added in schema version 3. */
  val icon: String? = null,
  /** Saturday and Sunday's reminder times, same format; null means the same as weekdays. Added in schema version 4. */
  val weekendReminderTimes: String? = null,
  /** One tap starts a session this long, and the log form's presets are built around it; null means the kind's default (SessionTimer). Added in schema version 5. */
  val sessionMinutes: Int? = null,
)

/** A stretch of days off (see domain TimeOff); [end] null while open. Added in schema version 4. */
@Entity(tableName = "time_off")
data class TimeOffRow(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val start: LocalDate,
  val end: LocalDate? = null,
)

@Dao
interface TimeOffDao {
  @Query("SELECT * FROM time_off ORDER BY start") fun observeAll(): Flow<List<TimeOffRow>>

  @Query("SELECT * FROM time_off ORDER BY start") suspend fun all(): List<TimeOffRow>

  @Insert suspend fun insert(row: TimeOffRow): Long

  @Insert suspend fun insertAll(rows: List<TimeOffRow>)

  @Update suspend fun update(row: TimeOffRow)

  @Query("DELETE FROM time_off WHERE id = :id") suspend fun delete(id: Long)

  @Query("DELETE FROM time_off") suspend fun deleteAll()
}

@Entity(
  tableName = "entries",
  foreignKeys = [ForeignKey(entity = Habit::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index("habitId", "day")],
)
data class Entry(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val habitId: Long,
  /** The day the session counts for, which can differ from when it was logged ("yesterday"). */
  val day: LocalDate,
  val type: EntryType = EntryType.SESSION,
  val score: Int? = null,
  val minutes: Int? = null,
  val note: String = "",
  /** Free text: other topics studied in the same session. */
  val extraTopics: String = "",
  /**
   * Course, certification or book the session belongs to, if any. For a reading habit (see domain
   * Books) it is the book's title, on its sessions and on its FINISHED mark.
   */
  val track: String? = null,
  /** The module or chapter of [track]; for a reading habit, the book's author. */
  val module: String? = null,
  val loggedAt: Long = System.currentTimeMillis(),
  /** Curriculum topic of a study session or of a KNOWN mark. Added in schema version 2. */
  val topicId: String? = null,
)

/** The reminder times that apply on [day]: the weekend list on Saturday and Sunday, when there is one. */
fun Habit.reminderTimesOn(day: LocalDate): List<java.time.LocalTime> =
  ReminderPlan.timesOn(day, parseReminderTimes(reminderTimes), weekendReminderTimes?.let(::parseReminderTimes))

val Habit.resolvedIcon: HabitIcon
  get() = HabitIcon.resolve(icon, kind, linkedPackage)

class Converters {
  @TypeConverter fun dayToLong(day: LocalDate): Long = day.toEpochDay()

  @TypeConverter fun longToDay(value: Long): LocalDate = LocalDate.ofEpochDay(value)

  @TypeConverter fun nullableDayToLong(day: LocalDate?): Long? = day?.toEpochDay()

  @TypeConverter fun longToNullableDay(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

  @TypeConverter fun kindToString(kind: HabitKind): String = kind.name

  @TypeConverter fun stringToKind(value: String): HabitKind = HabitKind.valueOf(value)

  @TypeConverter fun typeToString(type: EntryType): String = type.name

  @TypeConverter fun stringToType(value: String): EntryType = EntryType.valueOf(value)
}

@Dao
interface HabitDao {
  @Query("SELECT * FROM habits ORDER BY position, id") fun observeAll(): Flow<List<Habit>>

  @Query("SELECT * FROM habits ORDER BY position, id") suspend fun all(): List<Habit>

  @Query("SELECT * FROM habits WHERE id = :id") suspend fun byId(id: Long): Habit?

  @Query("SELECT COUNT(*) FROM habits") suspend fun count(): Int

  @Insert suspend fun insert(habit: Habit): Long

  @Insert suspend fun insertAll(habits: List<Habit>)

  @Query("DELETE FROM habits") suspend fun deleteAll()

  @Update suspend fun update(habit: Habit)
}

@Dao
interface EntryDao {
  @Query("SELECT * FROM entries ORDER BY day, loggedAt") fun observeAll(): Flow<List<Entry>>

  @Query("SELECT * FROM entries ORDER BY day, loggedAt") suspend fun all(): List<Entry>

  @Query("SELECT * FROM entries WHERE habitId = :habitId ORDER BY day, loggedAt") suspend fun forHabit(habitId: Long): List<Entry>

  @Insert suspend fun insert(entry: Entry): Long

  @Insert suspend fun insertAll(entries: List<Entry>)

  @Update suspend fun update(entry: Entry)

  @Query("SELECT * FROM entries WHERE id = :id") suspend fun byId(id: Long): Entry?

  @Query("DELETE FROM entries WHERE id = :id") suspend fun delete(id: Long)

  @Query("DELETE FROM entries") suspend fun deleteAll()

  @Query("DELETE FROM entries WHERE habitId = :habitId AND type = 'KNOWN' AND topicId = :topicId")
  suspend fun deleteKnown(habitId: Long, topicId: String)
}

@Database(
  entities = [Habit::class, Entry::class, TimeOffRow::class],
  version = DB_SCHEMA,
  exportSchema = true,
  autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5, spec = AddReading::class), AutoMigration(from = 5, to = 6, spec = AddChores::class)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun habits(): HabitDao

  abstract fun entries(): EntryDao

  abstract fun timeOff(): TimeOffDao

  companion object {
    fun create(context: Context): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "habits.db").build()
  }
}

/**
 * Schema 5 adds a habit's own session length, and Reading with it: 20 minutes of a paper book a day,
 * no linked app. Added once, on the phone that already has Study and Meditation; a fresh install gets
 * it from [HabitRepository.seedIfEmpty]. Study and Meditation still on their first times move off
 * working hours on weekdays and keep those times for the weekend; times already changed by hand stay.
 */
class AddReading : AutoMigrationSpec {
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    for ((first, habit) in listOf(STUDY_FIRST_TIMES to STUDY, MEDITATION_FIRST_TIMES to MEDITATION)) {
      db.execSQL(
        "UPDATE habits SET reminderTimes = ?, weekendReminderTimes = ? WHERE name = ? AND reminderTimes = ? AND weekendReminderTimes IS NULL",
        arrayOf<Any>(habit.reminderTimes, habit.weekendReminderTimes!!, habit.name, first),
      )
    }
    db.query("SELECT COUNT(*) FROM habits WHERE name = 'Reading'").use { if (it.moveToFirst() && it.getInt(0) > 0) return }
    val position = db.query("SELECT COALESCE(MAX(position) + 1, 0) FROM habits").use { if (it.moveToFirst()) it.getInt(0) else 0 }
    db.execSQL(
      "INSERT INTO habits (name, kind, reminderTimes, linkedPackage, position, icon, weekendReminderTimes, sessionMinutes) VALUES (?, ?, ?, NULL, ?, ?, ?, ?)",
      arrayOf<Any>(READING.name, READING.kind.name, READING.reminderTimes, position, READING.icon!!, READING.weekendReminderTimes!!, READING.sessionMinutes!!),
    )
  }
}

/**
 * Schema 6 changes no table: it adds Chores, 15 minutes a day of putting the place right (see domain
 * Chores), with the same reminders every day. Added once, like Reading; a fresh install gets it from
 * [HabitRepository.seedIfEmpty].
 */
class AddChores : AutoMigrationSpec {
  override fun onPostMigrate(db: SupportSQLiteDatabase) {
    db.query("SELECT COUNT(*) FROM habits WHERE name = 'Chores'").use { if (it.moveToFirst() && it.getInt(0) > 0) return }
    val position = db.query("SELECT COALESCE(MAX(position) + 1, 0) FROM habits").use { if (it.moveToFirst()) it.getInt(0) else 0 }
    db.execSQL(
      "INSERT INTO habits (name, kind, reminderTimes, linkedPackage, position, icon, weekendReminderTimes, sessionMinutes) VALUES (?, ?, ?, NULL, ?, ?, NULL, ?)",
      arrayOf<Any>(CHORES.name, CHORES.kind.name, CHORES.reminderTimes, position, CHORES.icon!!, CHORES.sessionMinutes!!),
    )
  }
}

// The habits as they're first set up. Weekdays stay off working hours (study from the end of the
// workday, meditation at lunch and in the evening, reading at lunch and before bed); weekends are
// spread out.
private const val STUDY_FIRST_TIMES = "09:30,13:30,17:30,19:30,21:30"
private const val MEDITATION_FIRST_TIMES = "08:00,11:00,15:00,21:45"

val STUDY = Habit(name = "Study", kind = HabitKind.STUDY, reminderTimes = "17:30,19:00,20:30,21:30", weekendReminderTimes = STUDY_FIRST_TIMES, icon = HabitIcon.BOOK.name)

val MEDITATION =
  Habit(
    name = "Meditation",
    kind = HabitKind.SIMPLE,
    reminderTimes = "13:00,18:30,21:45",
    weekendReminderTimes = MEDITATION_FIRST_TIMES,
    linkedPackage = "meditofoundation.medito",
    icon = HabitIcon.LOTUS.name,
  )

val READING =
  Habit(
    name = "Reading",
    kind = HabitKind.SIMPLE,
    reminderTimes = "13:30,21:00,22:30",
    weekendReminderTimes = "10:30,16:00,21:00,22:30",
    icon = HabitIcon.BOOKMARK.name,
    sessionMinutes = 20,
  )

/** Morning, lunch and the end of the workday, spaced out, weekends the same; the last one is the last call. */
val CHORES =
  Habit(
    name = "Chores",
    kind = HabitKind.SIMPLE,
    reminderTimes = "09:00,13:00,18:00",
    icon = HabitIcon.HOUSE.name,
    sessionMinutes = 15,
  )
