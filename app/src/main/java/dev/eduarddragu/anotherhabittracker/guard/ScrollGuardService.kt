package dev.eduarddragu.anotherhabittracker.guard

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.domain.GuardAction
import dev.eduarddragu.anotherhabittracker.domain.GuardTracker
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * Watches which app is in front, and nothing else: only window changes, never the screen's content
 * (`canRetrieveWindowContent` is off). The bookkeeping lives in [GuardTracker]; this class feeds it
 * events and carries out what it says: count time, set a timer, open [GuardActivity] over a guarded
 * app.
 *
 * Accessibility services may start activities from the background, which is why this is one: an
 * overlay alone can't tell when an app opens without polling usage stats every few seconds.
 */
class ScrollGuardService : AccessibilityService() {
  private val app
    get() = application as HabitApp

  /** A failure here must never take the app down: Android turns off a service that keeps crashing. */
  private val scope = MainScope() + CoroutineExceptionHandler { _, error -> Log.e(HabitApp.TAG, "Scroll guard failed", error) }
  private val handler = Handler(Looper.getMainLooper())
  private val timeUp = Runnable { run(tracker.onTimeUp()) }
  private val tracker = GuardTracker(isGuarded = { it in app.guard.packages.value }, now = SystemClock::elapsedRealtime)

  private val screen =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
          Intent.ACTION_SCREEN_OFF -> run(tracker.onScreenOff())
          Intent.ACTION_USER_PRESENT -> run(tracker.onScreenOn())
          // Woken within the lock's grace time (or with no lock): the keyguard never shows, so no
          // USER_PRESENT follows. With the keyguard up, USER_PRESENT comes once it's dismissed.
          Intent.ACTION_SCREEN_ON -> if (!getSystemService(KeyguardManager::class.java).isKeyguardLocked) run(tracker.onScreenOn())
          Intent.ACTION_DATE_CHANGED -> run(tracker.onDayChanged())
          // Another keyboard picked, or apps (keyboards among them) installed or removed.
          Intent.ACTION_INPUT_METHOD_CHANGED, Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED -> keyboards = null
        }
      }
    }

  override fun onServiceConnected() {
    registerReceiver(
      screen,
      IntentFilter().apply {
        addAction(Intent.ACTION_SCREEN_OFF)
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_USER_PRESENT)
        addAction(Intent.ACTION_DATE_CHANGED)
        addAction(Intent.ACTION_INPUT_METHOD_CHANGED)
      },
    )
    registerReceiver(
      screen,
      IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addDataScheme("package")
      },
    )
    if (!getSystemService(PowerManager::class.java).isInteractive) run(tracker.onScreenOff())
    // A grant saved on the guard's screen: count from now, without waiting for a window change.
    scope.launch { app.guard.grants.drop(1).collect { run(tracker.onGrant()) } }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent) {
    val pkg = event.packageName?.toString() ?: return
    // Coming back through Recents (or the quick-switch swipe) can bring an app to the front without a
    // window change. A guarded app that scrolls is in front, whatever the last window change said:
    // that's the doomscroll itself, so it can't slip past. Only the package is read, never the content.
    if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
      run(tracker.onScrolled(pkg, event.eventTime))
      return
    }
    if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
    // The guard itself sits over the guarded app without replacing it; the system UI (the shade, the
    // volume panel) and keyboards open on top of an app without leaving it.
    val className = event.className?.toString() ?: return
    if (className == GuardActivity::class.java.name) return
    // Only an activity coming up changes the app in front: a dialog, a popup or another app's floating
    // window (the share sheet, chat heads) leaves no event behind when it goes, so taking it for a
    // change would stop the count until the next real one. Both checks are cached, so a window change
    // costs no call to the system once an activity has been seen.
    if (pkg == SYSTEM_UI || !isActivity(pkg, className) || isKeyboard(pkg)) return
    run(tracker.onFront(pkg, event.eventTime))
  }

  override fun onInterrupt() = Unit

  override fun onDestroy() {
    run(tracker.onScreenOff())
    runCatching { unregisterReceiver(screen) }
    scope.cancel()
    super.onDestroy()
  }

  private fun run(actions: List<GuardAction>) {
    actions.forEach { action ->
      when (action) {
        is GuardAction.Use -> app.guard.use(action.day, action.millis)
        GuardAction.CancelTimer -> handler.removeCallbacks(timeUp)
        is GuardAction.Schedule -> handler.postDelayed(timeUp, action.millis)
        is GuardAction.Block -> runCatching { startActivity(GuardActivity.intent(this, action.pkg)) }.onFailure { Log.e(HabitApp.TAG, "Couldn't open the guard", it) }
        is GuardAction.Evaluate ->
          scope.launch {
            // Computed now, for the clock's today: the shared flow's last value can still be
            // yesterday's around midnight (its day moves on a poll that stalls in deep sleep).
            val open = app.repository.statuses().any { it.summary.dayOpen }
            val today = app.repository.today()
            val untilMidnight = Duration.between(LocalDateTime.now(), today.plusDays(1).atStartOfDay()).toMillis().coerceAtLeast(0)
            run(tracker.onEvaluated(action.generation, open, app.guard.budget(today), untilMidnight))
          }
      }
    }
  }

  /**
   * The keyboards' packages, read again when a keyboard is picked or an app installed or removed, and
   * at least every few minutes in case a broadcast was missed. The current one is included, in case the
   * list is filtered by package visibility.
   */
  private var keyboards: Set<String>? = null
  private var keyboardsAt = 0L

  private fun isKeyboard(pkg: String): Boolean {
    val now = SystemClock.elapsedRealtime()
    val known = keyboards?.takeIf { now - keyboardsAt < KEYBOARDS_MAX_AGE }
    val current =
      known
        ?: (getSystemService(InputMethodManager::class.java)?.inputMethodList.orEmpty().map { it.packageName } +
            listOfNotNull(Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')))
          .toSet()
          .also {
            keyboards = it
            keyboardsAt = now
          }
    return pkg in current
  }

  /** Whether a window's class is an activity, per class seen; bounded, the service lives as long as the process. */
  private val activities =
    object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > MAX_ACTIVITIES
    }

  private fun isActivity(pkg: String, className: String): Boolean =
    activities.getOrPut("$pkg/$className") { runCatching { packageManager.getActivityInfo(ComponentName(pkg, className), 0) }.isSuccess }

  private companion object {
    const val SYSTEM_UI = "com.android.systemui"
    const val KEYBOARDS_MAX_AGE = 5 * 60_000L
    const val MAX_ACTIVITIES = 256
  }
}
