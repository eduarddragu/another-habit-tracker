package dev.eduarddragu.anotherhabittracker.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import dev.eduarddragu.anotherhabittracker.domain.FocusSession
import dev.eduarddragu.anotherhabittracker.domain.SessionKind
import dev.eduarddragu.anotherhabittracker.domain.SessionTimer
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one focus session, if any, kept across process death. Times are on the elapsed-realtime clock
 * (immune to the clock corrections the phone makes every day or two); after a reboot that clock
 * restarts, so saved times are carried over through the wall clock once.
 */
class SessionStore(private val context: Context) {
  private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
  private val _session = MutableStateFlow(load())

  val session: StateFlow<FocusSession?> = _session.asStateFlow()

  fun now(): Long = SystemClock.elapsedRealtime()

  /**
   * The countdown notification was swiped away (since Android 14 an ongoing notification can be,
   * outside a foreground service): it isn't posted again until the session changes by hand.
   */
  val hidden: Boolean
    get() = prefs.getBoolean("hidden", false)

  fun hide() {
    if (_session.value != null) prefs.edit { putBoolean("hidden", true) }
  }

  /** [hidden]: whether the countdown stays hidden; any change made by hand shows it again. */
  fun set(session: FocusSession?, hidden: Boolean = false) {
    prefs.edit {
      clear()
      if (session != null) {
        if (hidden) putBoolean("hidden", true)
        putLong("habit", session.habitId)
        putString("habitName", session.habitName)
        putString("topic", session.topicId)
        putString("kind", session.kind.name)
        putString("day", session.day.toString())
        putLong("planned", session.plannedMillis)
        putLong("started", session.startedAt)
        putLong("extended", session.extendedMillis)
        session.pausedAt?.let { putLong("paused", it) }
        putLong("pausedTotal", session.pausedTotalMillis)
        session.finishedAt?.let { putLong("finished", it) }
        putInt("boot", bootCount())
        putLong("wall", System.currentTimeMillis())
        putLong("elapsed", now())
      }
    }
    _session.value = session
  }

  fun update(change: (FocusSession) -> FocusSession?) = _session.value?.let { set(change(it)) }

  private fun load(): FocusSession? {
    if (!prefs.contains("habit")) return null
    // Same boot: the saved times are on today's clock. After a reboot, they move with it.
    val shift = SessionTimer.rebootShift(prefs.getInt("boot", -1) == bootCount(), prefs.getLong("wall", 0), prefs.getLong("elapsed", 0), System.currentTimeMillis(), now())
    fun time(key: String) = if (prefs.contains(key)) prefs.getLong(key, 0) else null
    return runCatching {
      FocusSession(
        habitId = prefs.getLong("habit", 0),
        habitName = prefs.getString("habitName", null) ?: "Session",
        topicId = prefs.getString("topic", null),
        day = LocalDate.parse(prefs.getString("day", null)),
        plannedMillis = prefs.getLong("planned", 0),
        startedAt = time("started")!!,
        extendedMillis = prefs.getLong("extended", 0),
        pausedAt = time("paused"),
        pausedTotalMillis = prefs.getLong("pausedTotal", 0),
        finishedAt = time("finished"),
        // Saved before sessions carried their kind: guessed from the topic.
        kind = prefs.getString("kind", null)?.let { name -> SessionKind.entries.firstOrNull { it.name == name } } ?: SessionKind.guess(prefs.getString("topic", null)),
      )
        .shifted(shift)
    }.getOrNull()
  }

  private fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
}
