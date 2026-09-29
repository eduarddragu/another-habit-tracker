package dev.eduarddragu.anotherhabittracker

import android.app.Application
import android.util.Log
import dev.eduarddragu.anotherhabittracker.backup.BackupStore
import dev.eduarddragu.anotherhabittracker.data.AppDatabase
import dev.eduarddragu.anotherhabittracker.data.FocusStore
import dev.eduarddragu.anotherhabittracker.data.HabitRepository
import dev.eduarddragu.anotherhabittracker.data.PickHistory
import dev.eduarddragu.anotherhabittracker.domain.Curriculum
import dev.eduarddragu.anotherhabittracker.guard.GuardStore
import dev.eduarddragu.anotherhabittracker.reminders.Notifications
import dev.eduarddragu.anotherhabittracker.reminders.QuietStore
import dev.eduarddragu.anotherhabittracker.reminders.ReminderScheduler
import dev.eduarddragu.anotherhabittracker.widget.HabitWidgets
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Holds the app-wide singletons; small enough that a DI framework isn't worth it. */
class HabitApp : Application() {
  /**
   * Background work must never take the process down: a crash here would repeat on every alarm,
   * since each one starts the process again.
   */
  val appScope =
    CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error -> Log.e(TAG, "Background work failed", error) })

  val database by lazy { AppDatabase.create(this) }
  val repository by lazy { HabitRepository(database, ::loadCurriculum, FocusStore(this), appScope) }
  val scheduler by lazy { ReminderScheduler(this) }
  val backups by lazy { BackupStore(this) }
  val pickHistory by lazy { PickHistory(this) }
  val quiet by lazy { QuietStore(this) }
  val guard by lazy { GuardStore(this) }

  override fun onCreate() {
    super.onCreate()
    Notifications.createChannels(this)
    appScope.launch {
      repository.seedIfEmpty()
      scheduler.scheduleAll(repository.habits())
      scheduler.scheduleMidnightRefresh()
    }
    // Widgets and the backup follow the data: any change (log, freeze, known topic, settings, new
    // day) redraws the widgets and asks for a backup a couple of minutes later. The first emission is
    // only the current state, which both already have.
    appScope.launch {
      repository.observeStatuses().distinctUntilChanged().drop(1).conflate().collect {
        HabitWidgets.refresh(this@HabitApp)
        backups.scheduleSave()
      }
    }
    // Remember the study topic each day showed, for sessions logged late.
    appScope.launch {
      repository.observeStatuses().collect { statuses ->
        statuses.forEach { status -> status.pick?.let { pickHistory.record(status.habit.id, status.today, it.topic.id) } }
      }
    }
  }

  private fun loadCurriculum(): Curriculum = Curriculum.parse(assets.open("curriculum.json").bufferedReader().use { it.readText() })

  companion object {
    const val TAG = "HabitTracker"
  }
}
