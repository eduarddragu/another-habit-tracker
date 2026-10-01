package dev.eduarddragu.anotherhabittracker.ui.guard

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.domain.GuardWeek
import dev.eduarddragu.anotherhabittracker.domain.ScrollGuard
import dev.eduarddragu.anotherhabittracker.guard.GuardActivity
import dev.eduarddragu.anotherhabittracker.ui.components.ScreenTitle
import dev.eduarddragu.anotherhabittracker.ui.components.SectionLabel
import dev.eduarddragu.anotherhabittracker.ui.components.TextAction
import dev.eduarddragu.anotherhabittracker.ui.components.cardOutline
import dev.eduarddragu.anotherhabittracker.ui.components.rememberArrival
import dev.eduarddragu.anotherhabittracker.ui.components.rise
import dev.eduarddragu.anotherhabittracker.ui.components.screenPadding
import dev.eduarddragu.anotherhabittracker.ui.settings.LaunchableApp
import dev.eduarddragu.anotherhabittracker.ui.settings.launchableApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class GuardSettingsViewModel(private val app: HabitApp) : ViewModel() {
  val packages = app.guard.packages

  val apps: StateFlow<List<LaunchableApp>> =
    flow {
        // The usual feeds first, when they're installed, then everything else by name.
        val all = withContext(Dispatchers.IO) { launchableApps(app.packageManager, app.packageName) }.sortedBy { FEEDS.indexOf(it.packageName).let { i -> if (i < 0) FEEDS.size else i } }
        emit(all)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun serviceEnabled() = app.guard.serviceEnabled()

  /** "Stopped you 3 times this week, 12 minutes let through." */
  fun weekLine(): String = GuardWeek.line(GuardWeek.summary(app.guard.days(), app.repository.today()))

  /** Minutes used today and minutes let through, for the status line. */
  fun usage(): Pair<Long, Long> = app.guard.budget(app.repository.today()).let { it.usedMillis / 60_000 to it.allowedMillis / 60_000 }

  fun toggle(pkg: String) {
    val current = app.guard.packages.value
    app.guard.setPackages(if (pkg in current) current - pkg else current + pkg)
  }
}

/** Feeds worth guarding, listed first in the chooser when installed. */
private val FEEDS =
  listOf(ScrollGuard.DEFAULT_PACKAGE, "com.zhiliaoapp.musically", "com.google.android.youtube", "com.twitter.android", "com.reddit.frontpage", "com.facebook.katana", "com.facebook.orca")

/**
 * The scroll guard's settings: whether it's on (Android only lets an accessibility service be turned on
 * by hand, in its settings), and which apps it watches.
 */
@Composable
fun GuardSettingsScreen(app: HabitApp, modifier: Modifier = Modifier, viewModel: GuardSettingsViewModel = viewModel { GuardSettingsViewModel(app) }) {
  val context = LocalContext.current
  val packages by viewModel.packages.collectAsStateWithLifecycle()
  val apps by viewModel.apps.collectAsStateWithLifecycle()
  // Turned on or off in the system settings, so read again every time the screen comes back.
  var enabled by remember { mutableStateOf(viewModel.serviceEnabled()) }
  LifecycleResumeEffect(Unit) {
    enabled = viewModel.serviceEnabled()
    onPauseOrDispose {}
  }
  var choosing by rememberSaveable { mutableStateOf(false) }
  val arrival = rememberArrival(4)
  val colors = MaterialTheme.colorScheme
  val openSettings = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
  val guarded = apps.filter { it.packageName in packages }

  Column(modifier.verticalScroll(rememberScrollState()).padding(screenPadding()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    Column(Modifier.rise(arrival[0]), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      ScreenTitle("Scroll guard", "Feed later")
      Text(
        "While a habit is open, these apps get a speed bump. The first pass is ${ScrollGuard.FIRST_GRANT_MINUTES} minutes, " +
          "then ${ScrollGuard.MORE_GRANT_MINUTES} at a time, counted only while the app is on screen. Log everything and it steps aside.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
      )
    }

    Column(Modifier.rise(arrival[1]), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SectionLabel("Status")
      if (enabled) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(if (guarded.isEmpty()) "On, but watching nothing yet" else "On", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
          TextAction("Turn off in Settings", onClick = openSettings)
        }
        val (used, allowed) = remember(enabled) { viewModel.usage() }
        if (allowed > 0) Text("$used of $allowed minutes used today.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        val week = remember(enabled) { viewModel.weekLine() }
        Text(week, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
      } else {
        Text("Off. Android only lets you turn it on by hand: Accessibility, then Downloaded apps, then Scroll guard.", style = MaterialTheme.typography.bodyLarge)
        Text(
          "Android will warn that it can see and control your screen. It can't: it's only told which app is open, and nothing leaves the phone.",
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
        Button(shape = MaterialTheme.shapes.medium, onClick = openSettings) { Text("Open Accessibility") }
      }
      TextAction("Preview", onClick = { context.startActivity(GuardActivity.intent(context, guarded.firstOrNull()?.packageName ?: ScrollGuard.DEFAULT_PACKAGE, preview = true)) })
    }

    Column(Modifier.rise(arrival[2]), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SectionLabel("Apps")
      if (guarded.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
      guarded.forEach { guardedApp ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(guardedApp.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
          TextAction("Remove", onClick = { viewModel.toggle(guardedApp.packageName) })
        }
      }
      OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { choosing = true }, border = cardOutline()) { Text("Choose apps") }
    }

    if (enabled) {
      Text(
        "The guard only learns which app is in front. It doesn't read what the apps show, and nothing leaves the phone.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        modifier = Modifier.rise(arrival[3]),
      )
    }
  }

  if (choosing) {
    AlertDialog(
      onDismissRequest = { choosing = false },
      confirmButton = { TextButton(onClick = { choosing = false }) { Text("Done") } },
      title = { Text("Guarded apps") },
      text = {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
          items(apps, key = { it.packageName }) { candidate ->
            val checked = candidate.packageName in packages
            Row(
              Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = { viewModel.toggle(candidate.packageName) }).padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(checked = checked, onCheckedChange = null)
              Text(candidate.label)
            }
          }
        }
      },
    )
  }
}
