package dev.eduarddragu.anotherhabittracker.ui.components

import android.content.Intent
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.eduarddragu.anotherhabittracker.reminders.resolveLinkedApp

/** "Open <app>" for a habit's linked app; renders nothing when none is set or it isn't installed. */
@Composable
fun OpenLinkedAppButton(packageName: String?) {
  val context = LocalContext.current
  val app = remember(packageName) { resolveLinkedApp(context, packageName) } ?: return
  OutlinedButton(onClick = { context.startActivity(Intent(app.launchIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, border = cardOutline()) { Text("Open ${app.label}") }
}
