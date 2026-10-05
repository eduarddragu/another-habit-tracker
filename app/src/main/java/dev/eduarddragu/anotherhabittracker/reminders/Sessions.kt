package dev.eduarddragu.anotherhabittracker.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.domain.FocusSession
import dev.eduarddragu.anotherhabittracker.domain.SessionPhase
import dev.eduarddragu.anotherhabittracker.domain.SessionTimer
import java.time.LocalDate

/**
 * Every change to the focus session goes through here: the store, then the notification (a countdown
 * while it runs, a chime when it's over) and the alarm at its end. No service runs meanwhile: the
 * notification's chronometer counts by itself and the alarm finishes the session.
 */
object Sessions {
  fun start(app: HabitApp, habitId: Long, habitName: String, topicId: String?, day: LocalDate, minutes: Int) =
    apply(app) { _ -> SessionTimer.start(habitId, habitName, topicId, day, minutes, app.sessions.now()) }

  fun pause(app: HabitApp) = change(app) { it.pause(app.sessions.now()) }

  fun resume(app: HabitApp) = change(app) { it.resume(app.sessions.now()) }

  fun extend(app: HabitApp) = change(app) { it.extend(app.sessions.now()) }

  fun end(app: HabitApp) = change(app) { it.finish(app.sessions.now()) }

  /**
   * Ended by the midnight refresh (a session from an earlier day, paused or past its end): the end
   * notification still offers to log it, but without a chime in the middle of the night.
   */
  fun endQuietly(app: HabitApp) = apply(app, quiet = true) { current -> current?.finish(app.sessions.now()) }

  /**
   * Re-arms the end alarm and re-posts the countdown (after a reboot or an update, which drop both),
   * unless it was swiped away.
   */
  fun refresh(app: HabitApp) = apply(app, keepHidden = true) { it }

  /** The countdown was swiped away: it stays away until the session changes by hand. */
  @Synchronized
  fun hide(app: HabitApp) = app.sessions.hide()

  /** Gone without a log (discarded, or logged: the log is the record). */
  fun clear(app: HabitApp) = apply(app) { null }

  /**
   * Undo of a discard: [session] comes back as it was, with its notification but without a second
   * chime. Only while nothing else has started meanwhile.
   */
  @Synchronized
  fun restore(app: HabitApp, session: FocusSession) {
    if (app.sessions.session.value == null) apply(app, quiet = true) { session }
  }

  private fun change(app: HabitApp, transform: (FocusSession) -> FocusSession) = apply(app) { current -> current?.let(transform) }

  /** Called when the app comes back, in case the end alarm was lost: a session past its end is finished. */
  fun settle(app: HabitApp) {
    val session = app.sessions.session.value ?: return
    if (session.finishedAt == null && session.phase(app.sessions.now()) == SessionPhase.FINISHED) end(app)
  }

  // One change at a time: the UI and the notification buttons run on the main thread, but receivers
  // (reboot, midnight) run in the background, and two finishes at once would chime twice.
  // [keepHidden]: not a change made by hand, so a countdown swiped away stays away.
  @Synchronized
  private fun apply(app: HabitApp, keepHidden: Boolean = false, quiet: Boolean = false, transform: (FocusSession?) -> FocusSession?) {
    val before = app.sessions.session.value
    val after = transform(before)
    val hidden = keepHidden && app.sessions.hidden
    app.sessions.set(after, hidden)
    val now = app.sessions.now()
    cancelAlarm(app)
    if (after == null) {
      Notifications.dismissSession(app)
      return
    }
    val topic = after.topicId?.let { app.repository.curriculum.byId[it] }
    when (after.phase(now)) {
      SessionPhase.RUNNING -> {
        armAlarm(app, after.endsAt(now))
        if (!hidden) Notifications.showSession(app, after, topic, now)
      }
      SessionPhase.PAUSED -> if (!hidden) Notifications.showSession(app, after, topic, now)
      // The chime only once, when it goes from counting to over. The countdown goes first, so it
      // never lingers (its buttons would act on a session that's over) if the end can't be posted.
      SessionPhase.FINISHED ->
        if (before?.finishedAt == null) {
          Notifications.dismissSession(app)
          Notifications.showSessionEnd(app, after, topic, now, silent = quiet)
        }
    }
  }

  @SuppressLint("MissingPermission")
  private fun armAlarm(context: Context, atElapsed: Long) {
    val alarms = context.getSystemService(AlarmManager::class.java)
    if (alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, endIntent(context))
    else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, endIntent(context))
  }

  private fun cancelAlarm(context: Context) = context.getSystemService(AlarmManager::class.java).cancel(endIntent(context))

  private fun endIntent(context: Context) = action(context, SessionReceiver.ACTION_END, REQUEST_ALARM)

  fun action(context: Context, action: String, request: Int): PendingIntent =
    PendingIntent.getBroadcast(context, request, Intent(context, SessionReceiver::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

  private const val REQUEST_ALARM = 910_000
}

/** The end alarm and the notification's buttons. */
class SessionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val app = context.applicationContext as HabitApp
    when (intent.action) {
      ACTION_END -> Sessions.end(app)
      ACTION_PAUSE -> Sessions.pause(app)
      ACTION_RESUME -> Sessions.resume(app)
      ACTION_EXTEND -> Sessions.extend(app)
      ACTION_HIDE -> Sessions.hide(app)
    }
  }

  companion object {
    const val ACTION_END = "dev.eduarddragu.anotherhabittracker.SESSION_END"
    const val ACTION_PAUSE = "dev.eduarddragu.anotherhabittracker.SESSION_PAUSE"
    const val ACTION_RESUME = "dev.eduarddragu.anotherhabittracker.SESSION_RESUME"
    const val ACTION_EXTEND = "dev.eduarddragu.anotherhabittracker.SESSION_EXTEND"
    /** The countdown swiped away (its delete intent). */
    const val ACTION_HIDE = "dev.eduarddragu.anotherhabittracker.SESSION_HIDE"
  }
}
