package dev.eduarddragu.anotherhabittracker.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Today's topic chosen by hand for a habit ("keep going"), valid only on [day]. */
data class Focus(val day: LocalDate, val topicId: String)

/** The "keep going" choices, one per habit, in SharedPreferences: small, per device, not worth a table. */
class FocusStore(context: Context) {
  private val prefs = context.getSharedPreferences("focus", Context.MODE_PRIVATE)
  private val _all = MutableStateFlow(load())

  val all: StateFlow<Map<Long, Focus>> = _all.asStateFlow()

  fun set(habitId: Long, focus: Focus?) {
    prefs.edit { if (focus == null) remove(habitId.toString()) else putString(habitId.toString(), "${focus.day}|${focus.topicId}") }
    _all.value = load()
  }

  private fun load(): Map<Long, Focus> =
    prefs.all.mapNotNull { (key, value) ->
      val id = key.toLongOrNull() ?: return@mapNotNull null
      val (day, topic) = (value as? String)?.split('|', limit = 2)?.takeIf { it.size == 2 } ?: return@mapNotNull null
      runCatching { id to Focus(LocalDate.parse(day), topic) }.getOrNull()
    }
      .toMap()
}
