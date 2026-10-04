package dev.eduarddragu.anotherhabittracker.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.eduarddragu.anotherhabittracker.MainActivity
import dev.eduarddragu.anotherhabittracker.R
import dev.eduarddragu.anotherhabittracker.data.Habit
import dev.eduarddragu.anotherhabittracker.domain.HabitKind
import dev.eduarddragu.anotherhabittracker.domain.FocusSession
import dev.eduarddragu.anotherhabittracker.domain.RecapText
import dev.eduarddragu.anotherhabittracker.domain.SessionPhase
import dev.eduarddragu.anotherhabittracker.domain.Topic
import dev.eduarddragu.anotherhabittracker.domain.ReminderText
import dev.eduarddragu.anotherhabittracker.domain.Tone
import java.time.LocalDate

object Notifications {
  // Sound and vibration of a channel can't be changed by the app once it exists (they belong to the
  // user from then on), so a behaviour change means new channel ids. v1 had vibration off, v2 a
  // vibration too soft to notice and the system sound, v3 pointed at its sounds by resource id (which
  // shifts when a raw resource is added).
  private const val CHANNEL_REMINDERS = "reminders_v4"
  private const val CHANNEL_LAST_CALL = "last_call_v4"
  /** The Sunday recap: its own category, so it can be turned off without touching reminders. */
  private const val CHANNEL_RECAP = "recap_v1"

  /** A focus session: the silent countdown, and the chime when it's over. */
  private const val CHANNEL_SESSION = "session_v1"
  private const val CHANNEL_SESSION_END = "session_end_v1"

  /** Bumped whenever a channel is added: the set below is created again once. */
  private const val CHANNELS_VERSION = "v5"
  private val RETIRED_CHANNELS = listOf("reminders", "last_call", "reminders_v2", "last_call_v2", "reminders_v3", "last_call_v3")

  // Timings in ms (off, on, off, on...) and amplitudes at full strength: the default amplitude of a
  // plain pattern is easy to miss in a pocket.
  private val REMINDER_TIMINGS = longArrayOf(0, 320, 140, 320)
  private val REMINDER_AMPLITUDES = intArrayOf(0, 255, 0, 255)
  private val LAST_CALL_TIMINGS = longArrayOf(0, 200, 110, 200, 110, 200, 180, 650)
  private val LAST_CALL_AMPLITUDES = intArrayOf(0, 180, 0, 220, 0, 255, 0, 255)

  /** Opens the app straight on the log screen of a habit. */
  const val EXTRA_LOG_HABIT_ID = "log_habit_id"

  /** Opens the app on a habit's page (for study: today's topic and its questions). */
  const val EXTRA_OPEN_HABIT_ID = "open_habit_id"

  /** Opens the app on Home, whatever screen it was left on (the widget's background). */
  const val EXTRA_OPEN_HOME = "open_home"

  /**
   * Creates the channels once per channel version: this runs on every process start, including the
   * ones an alarm causes, and deleting and creating channels is binder work on the main thread.
   */
  fun createChannels(context: Context) {
    val prefs = context.getSharedPreferences("notifications", Context.MODE_PRIVATE)
    val manager = context.getSystemService(NotificationManager::class.java)
    if (prefs.getString(KEY_CHANNELS, null) == CHANNELS_VERSION && manager.getNotificationChannel(CHANNEL_REMINDERS) != null) return
    RETIRED_CHANNELS.forEach(manager::deleteNotificationChannel)
    manager.createNotificationChannels(
      listOf(
        NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
          description = "Daily reminders until a habit is logged"
          configure(context, "reminder_chime", REMINDER_TIMINGS, REMINDER_AMPLITUDES)
        },
        NotificationChannel(CHANNEL_LAST_CALL, "Last call", NotificationManager.IMPORTANCE_HIGH).apply {
          description = "The final reminder of the day, when the streak is at stake"
          configure(context, "last_call_chime", LAST_CALL_TIMINGS, LAST_CALL_AMPLITUDES)
        },
        NotificationChannel(CHANNEL_SESSION, "Session", NotificationManager.IMPORTANCE_LOW).apply {
          description = "The countdown while a session runs, with today's topic"
          setSound(null, null)
          enableVibration(false)
        },
        NotificationChannel(CHANNEL_SESSION_END, "Session over", NotificationManager.IMPORTANCE_HIGH).apply {
          description = "When a session's time is up"
          configure(context, "reminder_chime", REMINDER_TIMINGS, REMINDER_AMPLITUDES)
        },
        NotificationChannel(CHANNEL_RECAP, "Weekly recap", NotificationManager.IMPORTANCE_DEFAULT).apply {
          description = "Sunday evening: how the week went"
          configure(context, "reminder_chime", REMINDER_TIMINGS, REMINDER_AMPLITUDES)
        },
      )
    )
    prefs.edit { putString(KEY_CHANNELS, CHANNELS_VERSION) }
  }

  /**
   * The app's own chime (a res/raw name, addressed by name: resource ids shift between builds and the
   * channel keeps the URI forever), and a vibration at explicit strength where the platform allows it
   * (API 36+).
   */
  private fun NotificationChannel.configure(context: Context, sound: String, timings: LongArray, amplitudes: IntArray) {
    val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    setSound("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/$sound".toUri(), attributes)
    enableVibration(true)
    vibrationPattern = timings
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) setVibrationEffect(VibrationEffect.createWaveform(timings, amplitudes, -1))
  }

  /** One notification per habit: each reminder replaces the previous one. */
  /** [day]: the day the reminder is about, so "Done" tapped just after midnight still logs it. */
  fun show(context: Context, habit: Habit, text: ReminderText, tone: Tone, day: LocalDate? = null) {
    val tapOpensPage = habit.kind == HabitKind.STUDY
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return
    val channel = if (tone == Tone.LAST_CALL) CHANNEL_LAST_CALL else CHANNEL_REMINDERS
    val logIntent = activityIntent(context, habit.id, EXTRA_LOG_HABIT_ID, requestOffset = 0)
    val tapIntent = if (tapOpensPage) activityIntent(context, habit.id, EXTRA_OPEN_HABIT_ID, requestOffset = 200_000) else logIntent
    val builder =
      NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(text.title)
        .setContentText(text.body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
        .setContentIntent(tapIntent)
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(if (tone == Tone.LAST_CALL) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
    // At most three buttons show. Study: Log and On it. A habit without a score (meditation): Done,
    // its app and On it; tapping the notification itself opens the log form.
    if (habit.kind == HabitKind.STUDY) builder.addAction(0, "Log", logIntent)
    // A habit without a score (meditation) can be marked done right here, without opening the app.
    if (habit.kind == HabitKind.SIMPLE) builder.addAction(0, "Done", actionIntent(context, habit.id, NotificationActionReceiver.ACTION_DONE, requestOffset = 300_000, day = day))
    openAppIntent(context, habit)?.let { (label, intent) -> builder.addAction(0, "Open $label", intent) }
    // Already on it: the reminders in between keep quiet for a while. Never offered on the last call.
    if (tone != Tone.LAST_CALL) builder.addAction(0, "On it", actionIntent(context, habit.id, NotificationActionReceiver.ACTION_ON_IT, requestOffset = 400_000))
    try {
      manager.notify(notificationId(habit.id), builder.build())
    } catch (_: SecurityException) {
      // Notification permission revoked between the check and the post.
    }
  }

  /**
   * The session while it runs or is paused: the topic, its questions to check the scope without
   * opening the app, and a countdown the system keeps by itself.
   */
  fun showSession(context: Context, session: FocusSession, habitName: String, topic: Topic?, now: Long) {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return
    val paused = session.phase(now) == SessionPhase.PAUSED
    val endWall = System.currentTimeMillis() + session.remaining(now)
    val ends = java.time.Instant.ofEpochMilli(endWall).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    val status = if (paused) "Paused. ${(session.remaining(now) + 59_999) / 60_000} min left." else "Ends $ends"
    val questions = topic?.hints?.mapIndexed { i, hint -> "${i + 1}. $hint" }?.joinToString("\n")
    val builder =
      NotificationCompat.Builder(context, CHANNEL_SESSION)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(if (paused) "Paused · ${topic?.title ?: habitName}" else topic?.title ?: habitName)
        .setContentText(status)
        // A status bar chip with the time left (Android 16+ Live Updates), so the countdown is in view
        // without opening the shade.
        .setRequestPromotedOngoing(true)
        .setShortCriticalText(if (paused) "Paused" else "${(session.remaining(now) + 59_999) / 60_000}m")
        .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(status, questions).joinToString("\n\n")))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        .setContentIntent(activityIntent(context, session.habitId, EXTRA_OPEN_HABIT_ID, requestOffset = 920_000))
        .setShowWhen(!paused)
        .setUsesChronometer(!paused)
        .setChronometerCountDown(true)
        .setWhen(endWall)
        .addAction(0, "+5 min", Sessions.action(context, SessionReceiver.ACTION_EXTEND, 910_001))
        .addAction(0, if (paused) "Resume" else "Pause", Sessions.action(context, if (paused) SessionReceiver.ACTION_RESUME else SessionReceiver.ACTION_PAUSE, 910_002))
        .addAction(0, "End", Sessions.action(context, SessionReceiver.ACTION_END, 910_003))
    post(manager, builder)
  }

  /** Time's up (or ended): a chime, and a way straight to the log form with the minutes filled in. */
  fun showSessionEnd(context: Context, session: FocusSession, topic: Topic?, now: Long) {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return
    val minutes = session.minutesToLog(now)
    val log =
      Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_LOG_HABIT_ID, session.habitId)
        .putExtra(EXTRA_LOG_MINUTES, minutes ?: 0)
        .putExtra(EXTRA_LOG_DAY, session.day.toEpochDay())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val body = if (minutes == null) "Too short to count." else "$minutes minutes${topic?.let { " on ${it.title}" } ?: ""}. Log it while it's fresh."
    val builder =
      NotificationCompat.Builder(context, CHANNEL_SESSION_END)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(if (session.remaining(now) > 0) "Session ended" else "Time's up")
        .setContentText(body)
        .setContentIntent(PendingIntent.getActivity(context, 910_004, log, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
    if (minutes != null) builder.addAction(0, "Log", PendingIntent.getActivity(context, 910_005, log, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    builder.addAction(0, "+5 min", Sessions.action(context, SessionReceiver.ACTION_EXTEND, 910_006))
    post(manager, builder)
  }

  fun dismissSession(context: Context) = NotificationManagerCompat.from(context).cancel(SESSION_ID)

  private fun post(manager: NotificationManagerCompat, builder: NotificationCompat.Builder) {
    try {
      manager.notify(SESSION_ID, builder.build())
    } catch (_: SecurityException) {
      // Notification permission revoked between the check and the post.
    }
  }

  /** The weekly recap; a tap opens Home. */
  fun showRecap(context: Context, recap: RecapText) {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return
    val open = Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_HOME, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val builder =
      NotificationCompat.Builder(context, CHANNEL_RECAP)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(recap.title)
        .setContentText(recap.body.lineSequence().firstOrNull())
        .setStyle(NotificationCompat.BigTextStyle().bigText(recap.body))
        .setContentIntent(PendingIntent.getActivity(context, RECAP_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setAutoCancel(true)
    try {
      manager.notify(RECAP_ID, builder.build())
    } catch (_: SecurityException) {
      // Notification permission revoked between the check and the post.
    }
  }

  fun dismiss(context: Context, habitId: Long) = NotificationManagerCompat.from(context).cancel(notificationId(habitId))

  /** Reminders are about a single day: at midnight whatever is still up is stale. */
  fun dismissAll(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.activeNotifications.filter { it.notification.channelId in REMINDER_CHANNELS }.forEach { manager.cancel(it.tag, it.id) }
  }

  fun isShowing(context: Context, habitId: Long): Boolean =
    context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == notificationId(habitId) }

  /**
   * Ways reminders can be silenced outside the app: the Reminders channel turned off, or the app's
   * battery use set to Restricted (which limits its alarms). Empty when everything can get through.
   */
  fun deliveryProblems(context: Context): List<String> {
    val manager = context.getSystemService(NotificationManager::class.java)
    val problems = mutableListOf<String>()
    if (manager.getNotificationChannel(CHANNEL_REMINDERS)?.importance == NotificationManager.IMPORTANCE_NONE) problems += "The Reminders notification category is turned off."
    if (manager.getNotificationChannel(CHANNEL_LAST_CALL)?.importance == NotificationManager.IMPORTANCE_NONE) problems += "The Last call notification category is turned off."
    if (context.getSystemService(android.app.ActivityManager::class.java).isBackgroundRestricted) problems += "Battery use is set to Restricted, which delays or blocks reminders."
    return problems
  }

  private val REMINDER_CHANNELS = setOf(CHANNEL_REMINDERS, CHANNEL_LAST_CALL)
  private const val KEY_CHANNELS = "channels_created"
  private const val RECAP_ID = 900_000
  private const val SESSION_ID = 900_001

  /** With [EXTRA_LOG_HABIT_ID]: minutes to prefill, and the day (epoch day) the session belongs to. */
  const val EXTRA_LOG_MINUTES = "log_minutes"
  const val EXTRA_LOG_DAY = "log_day"

  private fun notificationId(habitId: Long) = habitId.toInt()

  private fun actionIntent(context: Context, habitId: Long, action: String, requestOffset: Int, day: LocalDate? = null): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      requestOffset + habitId.toInt(),
      Intent(context, NotificationActionReceiver::class.java)
        .setAction(action)
        .putExtra(NotificationActionReceiver.EXTRA_HABIT_ID, habitId)
        .apply { day?.let { putExtra(NotificationActionReceiver.EXTRA_DAY, it.toEpochDay()) } },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private fun activityIntent(context: Context, habitId: Long, extra: String, requestOffset: Int): PendingIntent {
    val intent =
      Intent(context, MainActivity::class.java).putExtra(extra, habitId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return PendingIntent.getActivity(
      context,
      requestOffset + habitId.toInt(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  /** Label and launch intent of the habit's linked app, when it is installed. */
  private fun openAppIntent(context: Context, habit: Habit): Pair<String, PendingIntent>? {
    val app = resolveLinkedApp(context, habit.linkedPackage) ?: return null
    val hop =
      Intent(context, OpenLinkedAppActivity::class.java)
        .putExtra(OpenLinkedAppActivity.EXTRA_HABIT_ID, habit.id)
        .putExtra(OpenLinkedAppActivity.EXTRA_PACKAGE, app.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    val pending =
      PendingIntent.getActivity(context, 100_000 + habit.id.toInt(), hop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    return app.label to pending
  }
}
