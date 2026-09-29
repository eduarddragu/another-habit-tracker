package dev.eduarddragu.anotherhabittracker.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

/**
 * The study topic shown on each of the last few days, per habit: the last one of the day wins, so a
 * swap with "I know this" is remembered too. A session logged late ("yesterday") takes its topic from
 * here, which is exactly what was on screen, even if the curriculum changed since.
 */
class PickHistory(context: Context) {
  private val prefs = context.getSharedPreferences("pick_history", Context.MODE_PRIVATE)

  fun topicOn(habitId: Long, day: LocalDate): String? = prefs.getString(key(habitId, day), null)

  fun record(habitId: Long, day: LocalDate, topicId: String) {
    if (topicOn(habitId, day) == topicId) return
    prefs.edit {
      putString(key(habitId, day), topicId)
      // Keep two weeks; older days are never logged late.
      prefs.all.keys.filter { dayOf(it)?.isBefore(day.minusDays(KEEP_DAYS)) == true }.forEach(::remove)
    }
  }

  private fun key(habitId: Long, day: LocalDate) = "$habitId@$day"

  private fun dayOf(key: String): LocalDate? = runCatching { LocalDate.parse(key.substringAfter('@')) }.getOrNull()

  private companion object {
    const val KEEP_DAYS = 14L
  }
}
