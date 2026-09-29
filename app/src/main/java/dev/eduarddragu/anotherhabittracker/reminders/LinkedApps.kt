package dev.eduarddragu.anotherhabittracker.reminders

import android.content.Context
import android.content.Intent

/** An installed app a habit can open: its label and launch intent. */
data class LinkedApp(val packageName: String, val label: String, val launchIntent: Intent)

fun resolveLinkedApp(context: Context, packageName: String?): LinkedApp? {
  val pkg = packageName ?: return null
  val pm = context.packageManager
  val launch = pm.getLaunchIntentForPackage(pkg) ?: return null
  val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
  return LinkedApp(pkg, label, launch)
}
