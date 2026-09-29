package dev.eduarddragu.anotherhabittracker.data

import android.content.Context
import androidx.room.AutoMigration
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
)

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
  /** Course, certification or book the session belongs to, if any. */
  val track: String? = null,
  val module: String? = null,
  val loggedAt: Long = System.currentTimeMillis(),
  /** Curriculum topic of a study session or of a KNOWN mark. Added in schema version 2. */
  val topicId: String? = null,
)

val Habit.resolvedIcon: HabitIcon
  get() = HabitIcon.resolve(icon, kind, linkedPackage)

class Converters {
  @TypeConverter fun dayToLong(day: LocalDate): Long = day.toEpochDay()

  @TypeConverter fun longToDay(value: Long): LocalDate = LocalDate.ofEpochDay(value)

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
  entities = [Habit::class, Entry::class],
  version = DB_SCHEMA,
  exportSchema = true,
  autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun habits(): HabitDao

  abstract fun entries(): EntryDao

  companion object {
    fun create(context: Context): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "habits.db").build()
  }
}
