package dev.eduarddragu.anotherhabittracker.reminders

import android.content.Context
import androidx.core.content.edit

/** "On it": per habit, the time until which its reminders stay quiet (the last call never does). */
class QuietStore(context: Context) {
  private val prefs = context.getSharedPreferences("quiet", Context.MODE_PRIVATE)

  fun until(habitId: Long): Long? = prefs.getLong(habitId.toString(), 0L).takeIf { it > 0 }

  fun quietFor(habitId: Long, millis: Long) = prefs.edit { putLong(habitId.toString(), System.currentTimeMillis() + millis) }

  /** After a restore: the ids may now name other habits. */
  fun clearAll() = prefs.edit { clear() }
}
