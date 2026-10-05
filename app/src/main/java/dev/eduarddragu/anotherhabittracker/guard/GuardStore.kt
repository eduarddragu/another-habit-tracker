package dev.eduarddragu.anotherhabittracker.guard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.core.content.edit
import dev.eduarddragu.anotherhabittracker.domain.GuardDay
import dev.eduarddragu.anotherhabittracker.domain.GuardWeek
import dev.eduarddragu.anotherhabittracker.domain.ScrollBudget
import dev.eduarddragu.anotherhabittracker.domain.ScrollGuard
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The scroll guard's settings (which apps it watches) and today's budget. */
class GuardStore(private val context: Context) {
  private val prefs = context.getSharedPreferences("guard", Context.MODE_PRIVATE)
  private val _packages = MutableStateFlow(prefs.getStringSet(KEY_PACKAGES, null)?.toSet() ?: setOf(ScrollGuard.DEFAULT_PACKAGE))

  /** The guarded apps: Instagram until a choice is saved. */
  val packages: StateFlow<Set<String>> = _packages.asStateFlow()

  fun setPackages(packages: Set<String>) {
    prefs.edit { putStringSet(KEY_PACKAGES, packages) }
    _packages.value = packages
  }

  fun budget(today: LocalDate): ScrollBudget {
    val day = prefs.getString(KEY_DAY, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return ScrollBudget(today)
    return ScrollBudget(day, prefs.getLong(KEY_USED, 0), prefs.getLong(KEY_ALLOWED, 0), prefs.getInt(KEY_GRANTS, 0)).on(today)
  }

  private val _grants = MutableStateFlow(0)

  /** Bumped by every grant, so the service can start counting as soon as the guard lets the feed through. */
  val grants: StateFlow<Int> = _grants.asStateFlow()

  fun grant(today: LocalDate) {
    save(budget(today).grant())
    _grants.value++
  }

  fun save(budget: ScrollBudget) {
    prefs.edit {
      putString(KEY_DAY, budget.day.toString())
      putLong(KEY_USED, budget.usedMillis)
      putLong(KEY_ALLOWED, budget.allowedMillis)
      putInt(KEY_GRANTS, budget.grants)
    }
    updateDay(budget.day) { it.copy(usedMillis = budget.usedMillis) }
  }

  /**
   * Guarded time spent on [day]. An earlier day than the budget's (counted up to midnight, booked
   * after a grant on the new day) only goes into the history: the budget is today's.
   */
  fun use(day: LocalDate, millis: Long) {
    val current = prefs.getString(KEY_DAY, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (current != null && day.isBefore(current)) updateDay(day) { it.copy(usedMillis = it.usedMillis + millis.coerceAtLeast(0)) }
    else save(budget(day).use(millis))
  }

  /** The guard stepped in (its screen came up) on [day]. */
  fun recordBlock(day: LocalDate) = updateDay(day) { it.copy(blocks = it.blocks + 1) }

  /** The last two weeks, day by day: times it stepped in, minutes it let through. */
  fun days(): List<GuardDay> =
    prefs.getString(KEY_HISTORY, null).orEmpty().split(";").mapNotNull { row ->
      val parts = row.split(",")
      if (parts.size != 3) return@mapNotNull null
      runCatching { GuardDay(LocalDate.parse(parts[0]), parts[1].toInt(), parts[2].toLong()) }.getOrNull()
    }

  private fun updateDay(day: LocalDate, change: (GuardDay) -> GuardDay) {
    val days = days().associateBy { it.day }.toMutableMap()
    days[day] = change(days[day] ?: GuardDay(day, 0, 0))
    val kept = GuardWeek.trim(days.values.sortedBy { it.day }, day)
    prefs.edit { putString(KEY_HISTORY, kept.joinToString(";") { "${it.day},${it.blocks},${it.usedMillis}" }) }
  }

  /** Whether the accessibility service is switched on in the system settings. */
  fun serviceEnabled(): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    val self = ComponentName(context, ScrollGuardService::class.java)
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
      it.resolveInfo.serviceInfo.let { info -> ComponentName(info.packageName, info.name) == self }
    }
  }

  private companion object {
    const val KEY_PACKAGES = "packages"
    const val KEY_DAY = "day"
    const val KEY_USED = "used"
    const val KEY_ALLOWED = "allowed"
    const val KEY_GRANTS = "grants"
    const val KEY_HISTORY = "history"
  }
}
