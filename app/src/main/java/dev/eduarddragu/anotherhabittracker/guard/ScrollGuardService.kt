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
    // change would stop the count until the next real one. Cheapest first: the activity check is
    // cached, the keyboard one asks the system.
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
   * Read each time: a keyboard installed after the service started is still a keyboard. The current
   * one is checked too, in case the list is filtered by package visibility.
   */
  private fun isKeyboard(pkg: String): Boolean =
    Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$pkg/") == true ||
      getSystemService(InputMethodManager::class.java)?.inputMethodList?.any { it.packageName == pkg } == true

  private val activities = HashMap<String, Boolean>()

  private fun isActivity(pkg: String, className: String): Boolean =
    activities.getOrPut("$pkg/$className") { runCatching { packageManager.getActivityInfo(ComponentName(pkg, className), 0) }.isSuccess }

  private companion object {
    const val SYSTEM_UI = "com.android.systemui"
  }
}
