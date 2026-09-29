package dev.eduarddragu.anotherhabittracker.guard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.core.content.edit
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

  fun save(budget: ScrollBudget) =
    prefs.edit {
      putString(KEY_DAY, budget.day.toString())
      putLong(KEY_USED, budget.usedMillis)
      putLong(KEY_ALLOWED, budget.allowedMillis)
      putInt(KEY_GRANTS, budget.grants)
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
  }
}
