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
import dev.eduarddragu.anotherhabittracker.domain.TimeOff
import dev.eduarddragu.anotherhabittracker.domain.TimeOffPeriod
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A habit, its sessions and freezes (newest first) and today's summary. */
data class HabitStatus(
  val habit: Habit,
  val summary: HabitSummary,
  val recent: List<Entry>,
  val since: LocalDate? = null,
  /** Today's topic was chosen by hand (keep going, or a review picked from the curriculum). */
  val focused: Boolean = false,
) {
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
  private val scope: CoroutineScope,
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
    combine(db.habits().observeAll(), db.entries().observeAll(), observeToday(), focus.all, db.timeOff().observeAll()) { habits, entries, today, _, timeOff ->
        val byHabit = entries.groupBy { it.habitId }
        val periods = timeOff.map { it.toPeriod() }
        val paused = TimeOff.days(periods, today)
        habits.map { status(it, byHabit[it.id].orEmpty(), today, paused, periods) }
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
      val periods = db.timeOff().all().map { it.toPeriod() }
      val paused = TimeOff.days(periods, today)
      db.habits().all().map { status(it, byHabit[it.id].orEmpty(), today, paused, periods) }
    }

  /** Every day off up to today. */
  suspend fun daysOff(): Set<LocalDate> = TimeOff.days(db.timeOff().all().map { it.toPeriod() }, today())

  /** Every time off period, oldest first. */
  fun observeTimeOff(): Flow<List<TimeOffPeriod>> = db.timeOff().observeAll().map { rows -> rows.map { it.toPeriod() } }

  /**
   * Starts time off on [start] (today or earlier), open or up to [end]. Periods sharing those days
   * keep only the days outside it (`TimeOff.outside`), so periods never overlap and no other day off
   * is lost.
   */
  suspend fun startTimeOff(start: LocalDate, end: LocalDate? = null): Unit =
    db.withTransaction {
      val rows = db.timeOff().all()
      // Days already off: nothing to add, and splitting that period would only move where it starts.
      if (rows.any { it.toPeriod().contains(start) && (it.end == null || (end != null && !it.end.isBefore(end))) }) return@withTransaction
      rows.forEach { row ->
        val period = row.toPeriod()
        val left = TimeOff.outside(period, start, end)
        if (left == listOf(period)) return@forEach
        if (left.isEmpty()) db.timeOff().delete(row.id)
        left.forEach { piece ->
          if (piece.id == row.id) db.timeOff().update(TimeOffRow(row.id, piece.start, piece.end)) else db.timeOff().insert(TimeOffRow(start = piece.start, end = piece.end))
        }
      }
      db.timeOff().insert(TimeOffRow(start = start, end = end))
    }

  /** Ends a period with [lastDay] off; one that would end before it starts is removed. */
  suspend fun endTimeOff(id: Long, lastDay: LocalDate) =
    db.withTransaction {
      val row = db.timeOff().all().firstOrNull { it.id == id } ?: return@withTransaction
      if (lastDay.isBefore(row.start)) db.timeOff().delete(id) else db.timeOff().update(row.copy(end = lastDay))
    }

  suspend fun deleteTimeOff(id: Long) = db.timeOff().delete(id)

  suspend fun habit(id: Long): Habit? = db.habits().byId(id)

  suspend fun habits(): List<Habit> = db.habits().all()

  suspend fun status(habitId: Long): HabitStatus? =
    db.habits().byId(habitId)?.let { habit ->
      val entries = db.entries().forHabit(habitId)
      val today = today()
      val periods = db.timeOff().all().map { it.toPeriod() }
      val paused = TimeOff.days(periods, today)
      withContext(Dispatchers.Default) { status(habit, entries, today, paused, periods) }
    }

  /**
   * A session for a day that was frozen replaces the freeze: the day was done after all, and this
   * week's freeze is free again.
   */
  suspend fun logSession(entry: Entry): Long =
    db.withTransaction {
      db.entries().forHabit(entry.habitId).filter { it.type == EntryType.FREEZE && it.day == entry.day }.forEach { db.entries().delete(it.id) }
      db.entries().insert(entry.copy(type = EntryType.SESSION))
    }

  /**
   * "Done" from a notification: logs [entry] unless its day already has a session. Checked in the
   * same transaction as the write, so two quick taps (or the form saving meanwhile) log only once.
   */
  suspend fun logSessionIfMissing(entry: Entry): Boolean =
    db.withTransaction {
      if (db.entries().forHabit(entry.habitId).any { it.type == EntryType.SESSION && it.day == entry.day }) return@withTransaction false
      logSession(entry)
      true
    }

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

  /**
   * An undo of "known" marks. Runs in the app's scope: the snackbar outlives the screen that showed
   * it, and that screen's ViewModel (and its scope) is gone once it's popped.
   */
  fun undoKnown(habitId: Long, topicIds: Set<String>) {
    scope.launch { db.withTransaction { topicIds.forEach { db.entries().deleteKnown(habitId, it) } } }
  }

  /** A study habit's topic as it was on [day] (for a session logged late). */
  suspend fun pickOn(habitId: Long, day: LocalDate): TopicPick? {
    val records = db.entries().forHabit(habitId).map { LogRecord(it.day, it.type, it.score, it.minutes, it.topicId) }
    return withContext(Dispatchers.Default) { TopicPicker.pickOn(curriculum, HabitSummaries.topicMarks(records), day) }
  }

  /** One consistent snapshot of everything, for the backup file. */
  suspend fun backup(appVersionCode: Long): BackupFile =
    db.withTransaction { backupOf(db.habits().all(), db.entries().all(), ZonedDateTime.now(clock), appVersionCode, curriculum.version, db.timeOff().all()) }

  /**
   * Replaces everything with [file], in one transaction: either the whole file is in, or nothing
   * changed. Returns the habits that were there before, so their alarms can be cancelled.
   */
  suspend fun restore(file: BackupFile): List<Habit> =
    db.withTransaction {
      val before = db.habits().all()
      db.entries().deleteAll()
      db.habits().deleteAll()
      db.timeOff().deleteAll()
      db.timeOff().insertAll(file.timeOff.map { it.toRow() })
      db.habits().insertAll(file.habits.map { it.toHabit() })
      // A freeze that shares its day with a session is dropped: the session replaced it.
      val sessionDays = file.entries.filter { it.type == EntryType.SESSION.name }.map { it.habitId to it.day }.toSet()
      db.entries().insertAll(file.entries.filterNot { it.type == EntryType.FREEZE.name && (it.habitId to it.day) in sessionDays }.map { it.toEntry() })
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

  private fun status(habit: Habit, entries: List<Entry>, today: LocalDate, paused: Set<LocalDate> = emptySet(), timeOff: List<TimeOffPeriod> = emptyList()): HabitStatus {
    val records = entries.map { LogRecord(it.day, it.type, it.score, it.minutes, it.topicId) }
    val built = HabitSummaries.build(habit.kind, records, today, paused) { curriculum }
    // A "keep going" choice for today replaces the picker's topic everywhere: screens, widget, reminders.
    // Marked as known since ("I know this" on it), it gives way to the picker; an undo brings it back.
    val chosen = focus.all.value[habit.id]?.takeIf { it.day == today }?.let { curriculum.byId[it.topicId] }?.takeIf { built.topicMarks[it.id]?.known != true }
    // Back from (or during) time off with nothing studied since it began: the topic of its first day
    // is held and offered again, until it's logged. A topic chosen by hand still wins.
    val held =
      if (chosen == null && habit.kind == HabitKind.STUDY) {
        TimeOff.heldSince(timeOff, today, entries.filter { it.type == EntryType.SESSION }.map { it.day }.toSet())?.let { start ->
          TopicPicker.pickOn(curriculum, HabitSummaries.topicMarks(records), start)?.topic?.takeIf { it.id !in built.topicMarks || built.topicMarks[it.id]?.known != true }
        }
      } else null
    // A chosen topic that is due for review reads as a review; otherwise it's the one being continued.
    val chosenKind = chosen?.let { topic -> if (built.topicMarks[topic.id]?.let { TopicPicker.dueDate(it)?.isAfter(today) == false } == true) PickKind.REVIEW else PickKind.CONTINUE }
    val summary =
      when {
        chosen != null && habit.kind == HabitKind.STUDY -> built.copy(pick = TopicPick(chosen, chosenKind!!))
        held != null -> built.copy(pick = TopicPick(held, PickKind.CONTINUE))
        else -> built
      }
    // Sessions and freezes: both are things he did on a day, and both can be taken back from Recent.
    val sessionDays = entries.filter { it.type == EntryType.SESSION }.map { it.day }.toSet()
    val recent = entries.filter { it.type == EntryType.SESSION || (it.type == EntryType.FREEZE && it.day !in sessionDays) }.sortedWith(compareByDescending<Entry> { it.day }.thenByDescending { it.loggedAt })
    return HabitStatus(habit, summary, recent, since = entries.minOfOrNull { it.day }, focused = chosen != null && habit.kind == HabitKind.STUDY)
  }
}
