package dev.eduarddragu.anotherhabittracker.data

import androidx.room.withTransaction
import dev.eduarddragu.anotherhabittracker.domain.BackupFile
import dev.eduarddragu.anotherhabittracker.domain.Cell
import dev.eduarddragu.anotherhabittracker.domain.Curriculum
import dev.eduarddragu.anotherhabittracker.domain.EntryType
import dev.eduarddragu.anotherhabittracker.domain.Freezes
import dev.eduarddragu.anotherhabittracker.domain.HabitIcon
import dev.eduarddragu.anotherhabittracker.domain.HabitKind
import dev.eduarddragu.anotherhabittracker.domain.HabitStats
import dev.eduarddragu.anotherhabittracker.domain.HabitSummaries
import dev.eduarddragu.anotherhabittracker.domain.HabitSummary
import dev.eduarddragu.anotherhabittracker.domain.LogRecord
import dev.eduarddragu.anotherhabittracker.domain.PhoneClock
import dev.eduarddragu.anotherhabittracker.domain.PickKind
import dev.eduarddragu.anotherhabittracker.domain.TopicMark
import dev.eduarddragu.anotherhabittracker.domain.TopicPick
import dev.eduarddragu.anotherhabittracker.domain.TopicPicker
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

/** A habit, its sessions and freezes (newest first) and today's summary. */
data class HabitStatus(val habit: Habit, val summary: HabitSummary, val recent: List<Entry>, val since: LocalDate? = null) {
  val today: LocalDate
    get() = summary.today

  val doneToday: Boolean
    get() = summary.doneToday

  val frozenToday: Boolean
    get() = summary.frozenToday

  val canFreezeToday: Boolean
    get() = summary.canFreezeToday

  val stats: HabitStats
    get() = summary.stats

  val streak: Int
    get() = summary.stats.streak

  val cells: Map<LocalDate, Cell>
    get() = summary.cells

  val pick: TopicPick?
    get() = summary.pick

  val topicMarks: Map<String, TopicMark>
    get() = summary.topicMarks
}

class HabitRepository(
  private val db: AppDatabase,
  private val loadCurriculum: () -> Curriculum,
  private val focus: FocusStore,
  scope: CoroutineScope,
  private val clock: Clock = PhoneClock,
) {
  val curriculum: Curriculum by lazy(loadCurriculum)

  fun today(): LocalDate = LocalDate.now(clock)

  /**
   * Today's date, re-checked every minute. A single long delay until midnight would stall while the
   * phone is in deep sleep, and a timezone change can move midnight.
   */
  private fun observeToday(): Flow<LocalDate> =
    merge(
        flow {
          while (true) {
            emit(today())
            delay(60_000)
          }
        },
        dayPokes.map { today() },
      )
      .distinctUntilChanged()

  /**
   * The minute poll above doesn't advance while the phone is in deep sleep, so the midnight alarm and
   * clock or timezone changes move the day on explicitly, before they refresh the widgets.
   */
  fun refreshDay() {
    dayPokes.value++
  }

  private val dayPokes = MutableStateFlow(0)

  /**
   * All statuses, computed once per change off the main thread and shared by every screen, the widget
   * and the app-level observer.
   */
  private val statuses: SharedFlow<List<HabitStatus>> =
    combine(db.habits().observeAll(), db.entries().observeAll(), observeToday(), focus.all) { habits, entries, today, _ ->
        val byHabit = entries.groupBy { it.habitId }
        habits.map { status(it, byHabit[it.id].orEmpty(), today) }
      }
      .flowOn(Dispatchers.Default)
      .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

  fun observeStatuses(): Flow<List<HabitStatus>> = statuses

  /**
   * The last computed status, if any. The shared flow's replay cache survives while no one listens,
   * so a screen being pushed can draw real content on its first frame instead of an empty one.
   */
  fun cachedStatus(habitId: Long): HabitStatus? = statuses.replayCache.firstOrNull()?.firstOrNull { it.habit.id == habitId }

  fun observeStatus(habitId: Long): Flow<HabitStatus?> = statuses.map { list -> list.firstOrNull { it.habit.id == habitId } }.distinctUntilChanged()

  /** One-shot version of observeStatuses, for widgets and receivers. */
  suspend fun statuses(): List<HabitStatus> =
    withContext(Dispatchers.Default) {
      val byHabit = db.entries().all().groupBy { it.habitId }
      val today = today()
      db.habits().all().map { status(it, byHabit[it.id].orEmpty(), today) }
    }

  suspend fun habit(id: Long): Habit? = db.habits().byId(id)

  suspend fun habits(): List<Habit> = db.habits().all()

  suspend fun status(habitId: Long): HabitStatus? =
    db.habits().byId(habitId)?.let { habit ->
      val entries = db.entries().forHabit(habitId)
      withContext(Dispatchers.Default) { status(habit, entries, today()) }
    }

  suspend fun logSession(entry: Entry): Long = db.entries().insert(entry.copy(type = EntryType.SESSION))

  /** Returns false when the rules don't allow a freeze on that day. */
  suspend fun freeze(habitId: Long, day: LocalDate): Boolean =
    db.withTransaction {
      val records = db.entries().forHabit(habitId)
      val sessions = records.filter { it.type == EntryType.SESSION }.map { it.day }.toSet()
      val freezes = records.filter { it.type == EntryType.FREEZE }.map { it.day }.toSet()
      if (!Freezes.canFreeze(day, sessions, freezes)) return@withTransaction false
      db.entries().insert(Entry(habitId = habitId, day = day, type = EntryType.FREEZE))
      true
    }

  suspend fun updateHabit(habit: Habit) = db.habits().update(habit)

  /** Keeps going on [topicId] today instead of the picker's topic; null goes back to the picker. */
  fun keepGoing(habitId: Long, topicId: String?) = focus.set(habitId, topicId?.let { Focus(today(), it) })

  /** Marks topics as already known today; topics already known are left alone. */
  /** Returns the topics that were newly marked, so an undo takes back exactly those. */
  suspend fun markKnown(habitId: Long, topicIds: Collection<String>): Set<String> =
    db.withTransaction {
      val already = db.entries().forHabit(habitId).filter { it.type == EntryType.KNOWN }.mapNotNull { it.topicId }.toSet()
      val fresh = topicIds.toSet() - already
      db.entries().insertAll(fresh.map { Entry(habitId = habitId, day = today(), type = EntryType.KNOWN, topicId = it) })
      fresh
    }

  suspend fun entry(id: Long): Entry? = db.entries().byId(id)

  /** A session corrected after the fact (score, minutes, notes, topic). */
  suspend fun updateEntry(entry: Entry) = db.entries().update(entry)

  /** A session or freeze taken back. */
  suspend fun deleteEntry(id: Long) = db.entries().delete(id)

  suspend fun unmarkKnown(habitId: Long, topicId: String) = db.entries().deleteKnown(habitId, topicId)

  /** A study habit's topic as it was on [day] (for a session logged late). */
  suspend fun pickOn(habitId: Long, day: LocalDate): TopicPick? {
    val records = db.entries().forHabit(habitId).map { LogRecord(it.day, it.type, it.score, it.minutes, it.topicId) }
    return withContext(Dispatchers.Default) { TopicPicker.pickOn(curriculum, HabitSummaries.topicMarks(records), day) }
  }

  /** One consistent snapshot of everything, for the backup file. */
  suspend fun backup(appVersionCode: Long): BackupFile =
    db.withTransaction { backupOf(db.habits().all(), db.entries().all(), ZonedDateTime.now(clock), appVersionCode, curriculum.version) }

  /**
   * Replaces everything with [file], in one transaction: either the whole file is in, or nothing
   * changed. Returns the habits that were there before, so their alarms can be cancelled.
   */
  suspend fun restore(file: BackupFile): List<Habit> =
    db.withTransaction {
      val before = db.habits().all()
      db.entries().deleteAll()
      db.habits().deleteAll()
      db.habits().insertAll(file.habits.map { it.toHabit() })
      db.entries().insertAll(file.entries.map { it.toEntry() })
      before
    }

  /** First launch only: the two habits the app was built for. */
  suspend fun seedIfEmpty() {
    db.withTransaction {
      if (db.habits().count() > 0) return@withTransaction
      db.habits().insert(Habit(name = "Study", kind = HabitKind.STUDY, reminderTimes = "09:30,13:30,17:30,19:30,21:30", position = 0, icon = HabitIcon.BOOK.name))
      db.habits()
        .insert(
          Habit(
            name = "Meditation",
            kind = HabitKind.SIMPLE,
            reminderTimes = "08:00,11:00,15:00,21:45",
            linkedPackage = "meditofoundation.medito",
            position = 1,
            icon = HabitIcon.LOTUS.name,
          )
        )
    }
  }

  private fun status(habit: Habit, entries: List<Entry>, today: LocalDate): HabitStatus {
    val records = entries.map { LogRecord(it.day, it.type, it.score, it.minutes, it.topicId) }
    val built = HabitSummaries.build(habit.kind, records, today) { curriculum }
    // A "keep going" choice for today replaces the picker's topic everywhere: screens, widget, reminders.
    val chosen = focus.all.value[habit.id]?.takeIf { it.day == today }?.let { curriculum.byId[it.topicId] }
    val summary = if (chosen != null && habit.kind == HabitKind.STUDY) built.copy(pick = TopicPick(chosen, PickKind.CONTINUE)) else built
    // Sessions and freezes: both are things he did on a day, and both can be taken back from Recent.
    val recent = entries.filter { it.type == EntryType.SESSION || it.type == EntryType.FREEZE }.sortedWith(compareByDescending<Entry> { it.day }.thenByDescending { it.loggedAt })
    return HabitStatus(habit, summary, recent, since = entries.minOfOrNull { it.day })
  }
}
