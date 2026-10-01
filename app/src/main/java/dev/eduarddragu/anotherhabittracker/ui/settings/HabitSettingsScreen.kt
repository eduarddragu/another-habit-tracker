package dev.eduarddragu.anotherhabittracker.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.data.Habit
import dev.eduarddragu.anotherhabittracker.data.resolvedIcon
import dev.eduarddragu.anotherhabittracker.domain.HabitIcon
import dev.eduarddragu.anotherhabittracker.domain.ReminderMessages
import dev.eduarddragu.anotherhabittracker.domain.ReminderPlan
import dev.eduarddragu.anotherhabittracker.domain.Tone
import dev.eduarddragu.anotherhabittracker.domain.formatReminderTimes
import dev.eduarddragu.anotherhabittracker.domain.formatTime
import dev.eduarddragu.anotherhabittracker.domain.parseReminderTimes
import dev.eduarddragu.anotherhabittracker.reminders.Notifications
import dev.eduarddragu.anotherhabittracker.reminders.ReminderScheduler
import dev.eduarddragu.anotherhabittracker.theme.Motion
import dev.eduarddragu.anotherhabittracker.theme.Numerals
import dev.eduarddragu.anotherhabittracker.ui.components.HabitIconImage
import dev.eduarddragu.anotherhabittracker.ui.components.HabitViewModel
import dev.eduarddragu.anotherhabittracker.ui.components.ScreenTitle
import dev.eduarddragu.anotherhabittracker.ui.components.SectionLabel
import dev.eduarddragu.anotherhabittracker.ui.components.TextAction
import dev.eduarddragu.anotherhabittracker.ui.components.cardOutline
import dev.eduarddragu.anotherhabittracker.ui.components.rememberArrival
import dev.eduarddragu.anotherhabittracker.ui.components.rise
import dev.eduarddragu.anotherhabittracker.ui.components.screenPadding
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HabitSettingsViewModel(app: HabitApp, habitId: Long) : HabitViewModel(app, habitId) {
  /** Launchable apps, loaded off the main thread: querying the package manager is slow. */
  val apps: StateFlow<List<LaunchableApp>> =
    flow { emit(withContext(Dispatchers.IO) { launchableApps(app.packageManager, app.packageName) }) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun save(habit: Habit, onDone: () -> Unit) = once {
    app.repository.updateHabit(habit)
    withContext(Dispatchers.IO) { app.scheduler.schedule(habit) }
    onDone()
  }

  /** Posts a sample of each tone, so the wording and the sound can be checked without waiting. */
  fun preview(habit: Habit) =
    viewModelScope.launch {
      val status = app.repository.status(habit.id)
      val tone = listOf(Tone.OPENING, Tone.NUDGE, Tone.PUSH, Tone.LAST_CALL)[previewCount++ % 4]
      val topic = status?.pick?.topic
      val text =
        ReminderMessages.text(
          topic?.title ?: habit.name,
          habit.kind,
          tone,
          status?.streak ?: 0,
          app.repository.today(),
          previewCount,
          topic?.hints?.firstOrNull(),
        )
      Notifications.show(app, habit, text, tone)
    }

  private var previewCount = 0
}

data class LaunchableApp(val label: String, val packageName: String)

@Composable
fun HabitSettingsScreen(
  app: HabitApp,
  habitId: Long,
  onDone: (message: String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: HabitSettingsViewModel = viewModel(key = "settings-$habitId") { HabitSettingsViewModel(app, habitId) },
) {
  val status by viewModel.status.collectAsStateWithLifecycle()
  val apps by viewModel.apps.collectAsStateWithLifecycle()
  val busy by viewModel.busy.collectAsStateWithLifecycle()
  val habit = status?.habit ?: return

  var name by rememberSaveable(habit.id) { mutableStateOf(habit.name) }
  var times by rememberSaveable(habit.id) { mutableStateOf(habit.reminderTimes) }
  var linkedPackage by rememberSaveable(habit.id) { mutableStateOf(habit.linkedPackage) }
  var icon by rememberSaveable(habit.id) { mutableStateOf(habit.resolvedIcon) }
  var pickingTime by rememberSaveable { mutableStateOf(false) }
  var pickingApp by rememberSaveable { mutableStateOf(false) }

  val linkedLabel = linkedPackage?.let { pkg -> apps.firstOrNull { it.packageName == pkg }?.label ?: "$pkg (not installed)" } ?: "None"

  // Title, then the habit itself, then its reminders and the rest, arriving as the page slides in.
  val arrival = rememberArrival(3)
  // Sections 24dp apart, 8dp inside a section, headed by a mono accent label like everywhere else.
  Column(modifier.verticalScroll(rememberScrollState()).padding(screenPadding()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    ScreenTitle("Settings", name.ifBlank { habit.name }, Modifier.rise(arrival[0], 16.dp), icon = icon)
    Column(Modifier.rise(arrival[1], 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SectionLabel("Name and icon")
      OutlinedTextField(name, { name = it }, label = { Text("Name") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), singleLine = true, modifier = Modifier.fillMaxWidth())
      IconPicker(icon, onPick = { icon = it })
    }

    Column(Modifier.rise(arrival[2], 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Reminders")
        val parsed = parseReminderTimes(times)
        parsed.forEachIndexed { index, time ->
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
              Text(formatTime(time), style = Numerals)
              Text(toneLabel(ReminderPlan.tone(index, parsed.size)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextAction("Remove", onClick = { times = formatReminderTimes(parsed - time) })
          }
        }
        // Adding a time and hearing what a reminder sounds like belong together.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
          if (parsed.size < ReminderScheduler.MAX_SLOTS) OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { pickingTime = true }, border = cardOutline()) { Text("Add reminder") }
          TextAction("Preview a reminder", onClick = { viewModel.preview(habit) })
        }
        if (parsed.size >= ReminderScheduler.MAX_SLOTS) {
          Text("That's the maximum of ${ReminderScheduler.MAX_SLOTS} reminders a day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Opens from the reminder")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
          Text(linkedLabel, style = MaterialTheme.typography.bodyLarge)
          TextAction("Change", onClick = { pickingApp = true })
        }
      }

      // The page ends on its main action, like the log form.
      Button(
        shape = MaterialTheme.shapes.medium,
        enabled = name.isNotBlank() && !busy,
        modifier = Modifier.fillMaxWidth(),
        onClick = { viewModel.save(habit.copy(name = name.trim(), reminderTimes = times, linkedPackage = linkedPackage, icon = icon.name)) { onDone("Saved") } },
      ) {
        Text("Save")
      }
    }
  }

  if (pickingTime) {
    TimeDialog(
      onDismiss = { pickingTime = false },
      onPick = {
        times = formatReminderTimes(parseReminderTimes(times) + it)
        pickingTime = false
      },
    )
  }
  if (pickingApp) {
    AppDialog(
      apps = apps,
      selected = linkedPackage,
      onDismiss = { pickingApp = false },
      onPick = {
        linkedPackage = it
        pickingApp = false
      },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPicker(selected: HabitIcon, onPick: (HabitIcon) -> Unit) {
  val haptics = LocalHapticFeedback.current
  val colors = MaterialTheme.colorScheme
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    HabitIcon.entries.forEach { icon ->
      val chosen = icon == selected
      // Fill and outline change together, both drawn at draw time.
      val fill by animateColorAsState(if (chosen) colors.primaryContainer else colors.primaryContainer.copy(alpha = 0f), tween(Motion.SHORT, easing = Motion.EaseUi), label = "icon")
      val edge by animateColorAsState(if (chosen) colors.primary else colors.outlineVariant, tween(Motion.SHORT, easing = Motion.EaseUi), label = "icon edge")
      Box(
        Modifier.size(48.dp)
          .clip(MaterialTheme.shapes.medium)
          .drawBehind {
            val stroke = 1.dp.toPx()
            val radius = CornerRadius(12.dp.toPx() - stroke / 2)
            drawRoundRect(fill, cornerRadius = radius)
            drawRoundRect(edge, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), radius, style = Stroke(stroke))
          }
          .selectable(chosen, role = Role.RadioButton) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onPick(icon)
          },
        contentAlignment = Alignment.Center,
      ) {
        val tint by animateColorAsState(if (chosen) colors.primary else colors.onSurfaceVariant, tween(Motion.SHORT, easing = Motion.EaseUi), label = "icon tint")
        HabitIconImage(icon, tint = tint)
      }
    }
  }
}

private fun toneLabel(tone: Tone) =
  when (tone) {
    Tone.OPENING -> "OPENS THE DAY"
    Tone.NUDGE -> "NUDGE"
    Tone.PUSH -> "PUSH"
    Tone.LAST_CALL -> "LAST CALL"
  }

// TimePicker is still marked experimental in Material 3.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
  val state = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("Add") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    text = { TimePicker(state = state) },
  )
}

@Composable
private fun AppDialog(apps: List<LaunchableApp>, selected: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    title = { Text("Open from the reminder") },
    text = {
      LazyColumn(Modifier.heightIn(max = 420.dp)) {
        item { AppRow("None", selected == null) { onPick(null) } }
        items(apps, key = { it.packageName }) { app -> AppRow(app.label, selected == app.packageName) { onPick(app.packageName) } }
      }
    },
  )
}

@Composable
private fun AppRow(label: String, selected: Boolean, onClick: () -> Unit) {
  Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    RadioButton(selected = selected, onClick = onClick)
    Text(label)
  }
}

/** Apps with a launcher icon, this one excluded, by label. Slow: call it off the main thread. */
fun launchableApps(pm: PackageManager, self: String): List<LaunchableApp> =
  pm
    .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    .map { LaunchableApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
    .filter { it.packageName != self }
    .distinctBy { it.packageName }
    .sortedBy { it.label.lowercase() }
