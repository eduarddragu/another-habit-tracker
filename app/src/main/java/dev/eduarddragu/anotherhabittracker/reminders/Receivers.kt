package dev.eduarddragu.anotherhabittracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.data.Entry
import dev.eduarddragu.anotherhabittracker.data.Habit
import dev.eduarddragu.anotherhabittracker.domain.EntryType
import dev.eduarddragu.anotherhabittracker.domain.ReminderMessages
import dev.eduarddragu.anotherhabittracker.domain.ReminderPlan
import dev.eduarddragu.anotherhabittracker.domain.parseReminderTimes
import dev.eduarddragu.anotherhabittracker.widget.HabitWidgets
import java.time.LocalTime
import kotlinx.coroutines.launch

/**
 * Runs [block] off the main thread while keeping the broadcast alive until it finishes. Failures are
 * logged, never thrown: an exception here would crash the process on every alarm.
 */
private fun BroadcastReceiver.runAsync(context: Context, block: suspend (HabitApp) -> Unit) {
  val app = context.applicationContext as HabitApp
  val pending = goAsync()
  app.appScope.launch {
    try {
      block(app)
    } catch (error: Exception) {
      Log.e(HabitApp.TAG, "${this@runAsync.javaClass.simpleName} failed", error)
    } finally {
      pending.finish()
    }
  }
}

/** Posts the reminder of [slot] for [habit] if its day is still open and he isn't already on it. */
private suspend fun remind(context: Context, app: HabitApp, habit: Habit, slot: Int) {
  val times = parseReminderTimes(habit.reminderTimes)
  if (slot >= times.size) return
  val status = app.repository.status(habit.id) ?: return
  if (!status.summary.dayOpen) return
  val tone = ReminderPlan.tone(slot, times.size)
  if (ReminderPlan.quieted(tone, System.currentTimeMillis(), app.quiet.until(habit.id))) return
  // Study reminders talk about today's topic and open with its first guiding question.
  val topic = status.pick?.topic
  val text =
    ReminderMessages.text(
      subject = topic?.title ?: habit.name,
      kind = habit.kind,
      tone = tone,
      streak = status.streak,
      day = status.today,
      slot = slot,
      openingBody = topic?.hints?.firstOrNull(),
    )
  Notifications.show(context, habit, text, tone)
}

/** Fired by the alarm of one reminder slot. */
class ReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION_REMIND) return
    val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1)
    val slot = intent.getIntExtra(EXTRA_SLOT, -1)
    if (habitId < 0 || slot < 0) return
    runAsync(context) { app ->
      val habit = app.repository.habit(habitId) ?: return@runAsync
      // Keep the chain going first, even if this reminder turns out to be unnecessary.
      app.scheduler.schedule(habit)
      remind(context, app, habit, slot)
    }
  }

  companion object {
    const val ACTION_REMIND = "dev.eduarddragu.anotherhabittracker.REMIND"
    const val EXTRA_HABIT_ID = "habit_id"
    const val EXTRA_SLOT = "slot"
  }
}

/**
 * Just after midnight: yesterday's reminders are stale, the day moves on, widgets roll over, every
 * reminder chain is renewed (a slot whose receiver once failed would otherwise stay silent), the next
 * midnight is booked, and the day that just ended goes into the backup.
 */
class MidnightReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION_MIDNIGHT) return
    runAsync(context) { app ->
      app.scheduler.scheduleMidnightRefresh()
      Notifications.dismissAll(context)
      app.repository.refreshDay()
      app.scheduler.scheduleAll(app.repository.habits())
      HabitWidgets.refresh(context)
      app.backups.scheduleSave(delayMinutes = 0)
    }
  }

  companion object {
    const val ACTION_MIDNIGHT = "dev.eduarddragu.anotherhabittracker.MIDNIGHT"
  }
}

/**
 * Alarms don't survive a reboot, and wall-clock changes invalidate them: schedule everything again.
 * After a reboot a reminder may have been missed, so the latest past slot of a still-open day is
 * posted once (unless its notification is already up). Clock and timezone changes only reschedule:
 * the phone corrects its clock every day or two, and posting then would bring back a reminder that
 * was already swiped away. App updates keep alarms, so they only reschedule too.
 */
class RescheduleReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action !in HANDLED) return
    val catchUp = intent.action == Intent.ACTION_BOOT_COMPLETED
    runAsync(context) { app ->
      val habits = app.repository.habits()
      app.repository.refreshDay()
      app.scheduler.scheduleAll(habits)
      app.scheduler.scheduleMidnightRefresh()
      if (catchUp) {
        val now = LocalTime.now()
        habits
          .filterNot { Notifications.isShowing(context, it.id) }
          .forEach { habit -> ReminderPlan.lastPassedSlot(parseReminderTimes(habit.reminderTimes), now)?.let { remind(context, app, habit, it) } }
      }
      HabitWidgets.refresh(context)
    }
  }

  private companion object {
    val HANDLED =
      setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
      )
  }
}

/** The buttons on a reminder that act without opening the app: "Done" and "On it". */
class NotificationActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1)
    if (habitId < 0) return
    when (intent.action) {
      ACTION_DONE ->
        runAsync(context) { app ->
          val status = app.repository.status(habitId) ?: return@runAsync
          // Logged like the form would, with the minutes of the last session (a usual length).
          if (status.summary.dayOpen) {
            val minutes = status.recent.firstOrNull { it.type == EntryType.SESSION }?.minutes
            app.repository.logSession(Entry(habitId = habitId, day = status.today, minutes = minutes))
          }
          Notifications.dismiss(context, habitId)
        }
      ACTION_ON_IT -> {
        (context.applicationContext as HabitApp).quiet.quietFor(habitId, ReminderPlan.ON_IT_MILLIS)
        Notifications.dismiss(context, habitId)
      }
    }
  }

  companion object {
    const val ACTION_DONE = "dev.eduarddragu.anotherhabittracker.DONE"
    const val ACTION_ON_IT = "dev.eduarddragu.anotherhabittracker.ON_IT"
    const val EXTRA_HABIT_ID = "habit_id"
  }
}
