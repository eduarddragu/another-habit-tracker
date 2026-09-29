package dev.eduarddragu.anotherhabittracker.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.eduarddragu.anotherhabittracker.data.Habit
import dev.eduarddragu.anotherhabittracker.domain.ReminderPlan
import dev.eduarddragu.anotherhabittracker.domain.parseReminderTimes
import java.time.ZonedDateTime

/**
 * One exact alarm per (habit, reminder slot). Every alarm fires daily: the receiver decides whether a
 * notification is still needed and schedules the same slot for the next day.
 */
class ReminderScheduler(private val context: Context) {
  private val alarms = context.getSystemService(AlarmManager::class.java)

  fun scheduleAll(habits: List<Habit>) = habits.forEach { schedule(it) }

  /** Stops every reminder of a habit that no longer exists (after a restore). */
  fun cancel(habitId: Long) {
    for (slot in 0 until MAX_SLOTS) {
      existingIntent(habitId, slot)?.let {
        alarms.cancel(it)
        it.cancel()
      }
    }
  }

  fun schedule(habit: Habit, now: ZonedDateTime = ZonedDateTime.now()) {
    // Request codes are habitId * MAX_SLOTS + slot, so more slots would collide with the next habit.
    val times = parseReminderTimes(habit.reminderTimes).take(MAX_SLOTS)
    times.forEachIndexed { slot, time ->
      val trigger = ReminderPlan.nextTrigger(time, now)
      setAlarm(trigger.toInstant().toEpochMilli(), pendingIntent(habit.id, slot))
    }
    // Slots removed from the habit must stop firing.
    for (slot in times.size until MAX_SLOTS) {
      existingIntent(habit.id, slot)?.let {
        alarms.cancel(it)
        it.cancel()
      }
    }
  }

  /**
   * Wakes the app just after midnight to roll widgets over to the new day and clear yesterday's
   * reminders. Exact: an inexact alarm may fire up to an hour late.
   */
  fun scheduleMidnightRefresh(now: ZonedDateTime = ZonedDateTime.now()) {
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusSeconds(30)
    val intent = Intent(context, MidnightReceiver::class.java).setAction(MidnightReceiver.ACTION_MIDNIGHT)
    val pending = PendingIntent.getBroadcast(context, MIDNIGHT_REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    setAlarm(nextMidnight.toInstant().toEpochMilli(), pending)
  }

  /**
   * USE_EXACT_ALARM is granted at install and can't be revoked, so the exact path is the normal one.
   * The inexact fallback only matters if that ever changes: a late reminder beats none.
   */
  // Lint only knows SCHEDULE_EXACT_ALARM; the app holds USE_EXACT_ALARM and checks canScheduleExactAlarms().
  @SuppressLint("MissingPermission")
  private fun setAlarm(triggerAtMillis: Long, operation: PendingIntent) {
    if (alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
    else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
  }

  private fun intent(habitId: Long, slot: Int) =
    Intent(context, ReminderReceiver::class.java)
      .setAction(ReminderReceiver.ACTION_REMIND)
      .putExtra(ReminderReceiver.EXTRA_HABIT_ID, habitId)
      .putExtra(ReminderReceiver.EXTRA_SLOT, slot)

  private fun requestCode(habitId: Long, slot: Int) = (habitId * MAX_SLOTS + slot).toInt()

  private fun pendingIntent(habitId: Long, slot: Int): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      requestCode(habitId, slot),
      intent(habitId, slot),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private fun existingIntent(habitId: Long, slot: Int): PendingIntent? =
    PendingIntent.getBroadcast(
      context,
      requestCode(habitId, slot),
      intent(habitId, slot),
      PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

  companion object {
    const val MAX_SLOTS = 24
    private const val MIDNIGHT_REQUEST = -1
  }
}
