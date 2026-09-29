package dev.eduarddragu.anotherhabittracker.reminders

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Invisible hop behind a reminder's "Open <app>" action: dismisses the reminder, then opens the linked
 * app. Notification action buttons don't auto-cancel, and Android 12+ only allows starting an activity
 * from a notification through an activity, not a broadcast receiver.
 */
class OpenLinkedAppActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1)
    val pkg = intent.getStringExtra(EXTRA_PACKAGE)
    if (habitId >= 0) Notifications.dismiss(this, habitId)
    pkg?.let { packageManager.getLaunchIntentForPackage(it) }?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    finish()
  }

  companion object {
    const val EXTRA_HABIT_ID = "habit_id"
    const val EXTRA_PACKAGE = "package"
  }
}
