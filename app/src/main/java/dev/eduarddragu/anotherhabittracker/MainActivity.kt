package dev.eduarddragu.anotherhabittracker

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import dev.eduarddragu.anotherhabittracker.reminders.Notifications
import dev.eduarddragu.anotherhabittracker.reminders.Sessions
import dev.eduarddragu.anotherhabittracker.theme.AnotherHabitTrackerTheme
import dev.eduarddragu.anotherhabittracker.widget.HabitWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private var pendingLogHabitId by mutableStateOf<Long?>(null)
  /** From a finished session: the minutes to prefill and the day it belongs to. */
  private var pendingLogMinutes by mutableStateOf<Int?>(null)
  private var pendingLogDay by mutableStateOf<Long?>(null)
  private var pendingOpenHabitId by mutableStateOf<Long?>(null)
  private var pendingHome by mutableStateOf(false)
  private var notificationsEnabled by mutableStateOf(true)
  private var deliveryProblems by mutableStateOf(emptyList<String>())

  private val prefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }

  private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshDeliveryState() }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) readLaunchRequest(intent)
    enableEdgeToEdge()
    refreshDeliveryState()
    // Ask once, on the first launch. After that only the "Turn on" button asks.
    if (!notificationsEnabled && !prefs.getBoolean(KEY_ASKED, false)) requestNotifications()
    if (!previewsPublished) {
      previewsPublished = true
      // Off the main thread and after the first frames: the preview is a whole Glance composition.
      lifecycleScope.launch(Dispatchers.Default) {
        delay(5_000)
        HabitWidgets.publishPreviews(applicationContext)
      }
    }
    val app = application as HabitApp
    setContent {
      AnotherHabitTrackerTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(
            app = app,
            notificationsEnabled = notificationsEnabled,
            deliveryProblems = deliveryProblems,
            onEnableNotifications = ::enableNotifications,
            pendingLogHabitId = pendingLogHabitId,
            pendingLogMinutes = pendingLogMinutes,
            pendingLogDay = pendingLogDay,
            onPendingLogConsumed = {
              pendingLogHabitId = null
              pendingLogMinutes = null
              pendingLogDay = null
            },
            pendingOpenHabitId = pendingOpenHabitId,
            onPendingOpenConsumed = { pendingOpenHabitId = null },
            pendingHome = pendingHome,
            onPendingHomeConsumed = { pendingHome = false },
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    // The user may have changed notification or battery settings while away.
    refreshDeliveryState()
    // The minute poll stalls in deep sleep: the day on screen may be stale after the screen wakes.
    (application as HabitApp).repository.refreshDay()
    // A session whose end alarm was lost (the phone off at the time) is finished now.
    Sessions.settle(application as HabitApp)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    readLaunchRequest(intent)
  }

  private fun refreshDeliveryState() {
    notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
    deliveryProblems = Notifications.deliveryProblems(this)
  }

  private fun requestNotifications() {
    prefs.edit { putBoolean(KEY_ASKED, true) }
    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
  }

  /** Asks again while Android still shows the prompt; once it stops, only the settings page can help. */
  private fun enableNotifications() {
    val canAsk = !prefs.getBoolean(KEY_ASKED, false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
    if (canAsk) requestNotifications()
    else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
  }

  private fun readLaunchRequest(intent: Intent?) {
    // Reopening a task from Recents replays its original intent: don't act on an old notification.
    if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
    val logId = intent.getLongExtra(Notifications.EXTRA_LOG_HABIT_ID, -1L)
    val openId = intent.getLongExtra(Notifications.EXTRA_OPEN_HABIT_ID, -1L)
    // Action buttons, unlike a tap on the notification body, don't dismiss the notification.
    if (logId >= 0) {
      Notifications.dismiss(this, logId)
      // The activity is exported: extras from anywhere else are trusted only within what a session
      // can produce (a log for today or yesterday, at most ten hours).
      pendingLogMinutes = intent.getIntExtra(Notifications.EXTRA_LOG_MINUTES, 0).takeIf { it in 1..MAX_LOG_MINUTES }
      val today = (application as HabitApp).repository.today().toEpochDay()
      pendingLogDay = intent.getLongExtra(Notifications.EXTRA_LOG_DAY, Long.MIN_VALUE).takeIf { it == today || it == today - 1 }
      pendingLogHabitId = logId
    } else if (openId >= 0) {
      pendingOpenHabitId = openId
    } else if (intent.getBooleanExtra(Notifications.EXTRA_OPEN_HOME, false)) {
      pendingHome = true
    }
  }

  private companion object {
    const val KEY_ASKED = "notifications_asked"
    const val MAX_LOG_MINUTES = 600

    /** Picker previews are rate-limited by the system: publish once per process. */
    var previewsPublished = false
  }
}
