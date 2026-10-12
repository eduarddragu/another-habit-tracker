package dev.eduarddragu.anotherhabittracker.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import dev.eduarddragu.anotherhabittracker.ui.components.gutter
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import dev.eduarddragu.anotherhabittracker.ui.components.Field
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.R
import dev.eduarddragu.anotherhabittracker.data.Habit
import dev.eduarddragu.anotherhabittracker.data.resolvedIcon
import dev.eduarddragu.anotherhabittracker.domain.Chores
import dev.eduarddragu.anotherhabittracker.domain.HabitIcon
import dev.eduarddragu.anotherhabittracker.domain.HabitKind
import dev.eduarddragu.anotherhabittracker.domain.ReminderMessages
import dev.eduarddragu.anotherhabittracker.domain.SessionTimer
import dev.eduarddragu.anotherhabittracker.domain.Tone
import dev.eduarddragu.anotherhabittracker.domain.formatReminderTimes
import dev.eduarddragu.anotherhabittracker.domain.formatTime
import dev.eduarddragu.anotherhabittracker.domain.parseReminderTimes
import dev.eduarddragu.anotherhabittracker.reminders.Notifications
import dev.eduarddragu.anotherhabittracker.reminders.ReminderScheduler
import dev.eduarddragu.anotherhabittracker.theme.Motion
import dev.eduarddragu.anotherhabittracker.ui.components.DayPickerDialog
import dev.eduarddragu.anotherhabittracker.ui.components.HabitIconImage
import dev.eduarddragu.anotherhabittracker.ui.components.HabitViewModel
import dev.eduarddragu.anotherhabittracker.ui.components.ScreenTitle
import dev.eduarddragu.anotherhabittracker.ui.components.SectionLabel
import dev.eduarddragu.anotherhabittracker.ui.components.TextAction
import dev.eduarddragu.anotherhabittracker.ui.components.cardOutline
import dev.eduarddragu.anotherhabittracker.ui.components.launchSafely
import dev.eduarddragu.anotherhabittracker.ui.components.rememberArrival
import dev.eduarddragu.anotherhabittracker.ui.components.rise
import dev.eduarddragu.anotherhabittracker.ui.components.screenPadding
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
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
    launchSafely {
      val status = app.repository.status(habit.id)
      val slot = previewCount++ % 4
      val tone = listOf(Tone.OPENING, Tone.NUDGE, Tone.PUSH, Tone.LAST_CALL)[slot]
      val topic = status?.pick?.topic
      val today = app.repository.today()
      val chores = if (Chores.appliesTo(habit.kind, habit.name, habit.icon)) Chores.reminderLine(today, slot) else null
      val text =
        ReminderMessages.text(
          topic?.title ?: habit.name,
          habit.kind,
          tone,
          status?.streak ?: 0,
          today,
          slot,
          topic?.hints?.firstOrNull() ?: chores,
          nudgeBody = chores,
          slipped = status?.summary?.slipped ?: false,
          minutes = SessionTimer.defaultMinutes(habit.kind, habit.sessionMinutes),
        )
      Notifications.show(app, habit, text, tone, preview = true)
    }

  private var previewCount = 0

  fun today(): LocalDate = app.repository.today()
}

data class LaunchableApp(val label: String, val packageName: String)

@Composable
fun HabitSettingsScreen(
  app: HabitApp,
  habitId: Long,
  /** Closes the page, with a message for the snackbar (null: closed without saving). */
  onDone: (message: String?) -> Unit,
  /** A message with Undo, for a reminder time taken off by a tap. */
  onUndoable: (String, () -> Unit) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: HabitSettingsViewModel = viewModel(key = "settings-$habitId") { HabitSettingsViewModel(app, habitId) },
) {
  val status by viewModel.status.collectAsStateWithLifecycle()
  val apps by viewModel.apps.collectAsStateWithLifecycle()
  val busy by viewModel.busy.collectAsStateWithLifecycle()
  val habit = status?.habit ?: return

  var name by rememberSaveable(habit.id) { mutableStateOf(habit.name) }
  var times by rememberSaveable(habit.id) { mutableStateOf(habit.reminderTimes) }
  // Null: the weekend uses the weekday times.
  var weekendTimes by rememberSaveable(habit.id) { mutableStateOf(habit.weekendReminderTimes) }
  var linkedPackage by rememberSaveable(habit.id) { mutableStateOf(habit.linkedPackage) }
  var icon by rememberSaveable(habit.id) { mutableStateOf(habit.resolvedIcon) }
  var sessionMinutes by rememberSaveable(habit.id) { mutableStateOf(SessionTimer.defaultMinutes(habit.kind, habit.sessionMinutes)) }
  var certId by rememberSaveable(habit.id) { mutableStateOf(habit.certId) }
  var examDay by rememberSaveable(habit.id) { mutableStateOf(habit.examDate?.toEpochDay()) }
  // The cert whose exam date is being picked: a cert is only set together with its date.
  var pickingExamFor by rememberSaveable { mutableStateOf<String?>(null) }
  // Which list a new time goes to: weekdays or the weekend.
  var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }
  // Which reminder list is on screen.
  var showingWeekend by rememberSaveable { mutableStateOf(false) }
  var pickingApp by rememberSaveable { mutableStateOf(false) }
  var confirmingBack by rememberSaveable { mutableStateOf(false) }

  // Back with something changed and not saved asks first: a reminder list takes a while to set up.
  val changed = name.trim() != habit.name || times != habit.reminderTimes || weekendTimes != habit.weekendReminderTimes || linkedPackage != habit.linkedPackage || icon != habit.resolvedIcon || sessionMinutes != SessionTimer.defaultMinutes(habit.kind, habit.sessionMinutes) || certId != habit.certId || examDay != habit.examDate?.toEpochDay()
  BackHandler(enabled = changed && !busy) { confirmingBack = true }

  val linkedLabel = linkedPackage?.let { pkg -> apps.firstOrNull { it.packageName == pkg }?.label ?: "$pkg (not installed)" } ?: "None"

  // Title, then the habit itself, then its reminders and the rest, arriving as the page slides in.
  val arrival = rememberArrival(3)
  // Sections 24dp apart, 8dp inside a section, headed by a mono accent label like everywhere else.
  Column(modifier) {
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(screenPadding()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    ScreenTitle("Settings", name.ifBlank { habit.name }, Modifier.rise(arrival[0], 16.dp), icon = icon)

    // Who the habit is first, then how it runs, and its reminders last, where they have room.
    Column(Modifier.rise(arrival[1], 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Name and icon")
        Field(name, { name = it }, label = "Name", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), singleLine = true, modifier = Modifier.fillMaxWidth())
        IconPicker(icon, onPick = { icon = it })
      }
      val certs = viewModel.curriculum.certs
      if (habit.kind == HabitKind.STUDY && certs.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          SectionLabel("Studying for")
          val cert = certs.firstOrNull { it.id == certId }
          val exam = examDay?.let(LocalDate::ofEpochDay)
          if (cert == null) {
            Text("Study for one exam until its date, instead of a topic a day. Passing it marks its topics as known.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              certs.forEach { OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { pickingExamFor = it.id }, border = cardOutline()) { Text(it.name) } }
            }
          } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(cert.name, style = MaterialTheme.typography.bodyLarge)
                Text(listOfNotNull(cert.code, exam?.let { "exam ${it.format(EXAM_FORMAT)}" } ?: "no exam date").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              val action = if (exam == null) "Set date" else "Change date"
              TextAction(action, onClick = { pickingExamFor = cert.id }, modifier = Modifier.semantics { contentDescription = exam?.let { "Change the exam date, now ${it.format(EXAM_FORMAT)}" } ?: "Set the exam date" })
            }
            // The way out, muted: it shouldn't invite a tap.
            TextAction(
              "Stop studying for it",
              onClick = {
                certId = null
                examDay = null
              },
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // One tap on the planet or the card starts a session this long; the log form's presets follow it.
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Session length")
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          (SessionTimer.LENGTHS + sessionMinutes).distinct().sorted().forEach { length ->
            FilterChip(
              selected = sessionMinutes == length,
              onClick = { sessionMinutes = length },
              label = { Text("$length min") },
              modifier = Modifier.semantics { role = Role.RadioButton },
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Opens from the reminder")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
          // A long label ("pkg (not installed)") wraps instead of pushing Change off the row.
          Text(linkedLabel, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
          TextAction("Change", onClick = { pickingApp = true }, modifier = Modifier.semantics { contentDescription = "Change the app that opens, now $linkedLabel" })
        }
      }
    }

    Column(Modifier.rise(arrival[2], 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
      // Workdays and weekends can differ: lunch and evening during the week, spread out on Saturday
      // and Sunday. One list shows at a time; a time's tone follows from its place in it.
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Reminders")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
          listOf("Weekdays", "Weekend").forEachIndexed { index, label ->
            SegmentedButton(
              selected = showingWeekend == (index == 1),
              onClick = { showingWeekend = index == 1 },
              shape = SegmentedButtonDefaults.itemShape(index, 2, MaterialTheme.shapes.medium),
              icon = {},
            ) {
              Text(label)
            }
          }
        }
        val weekend = weekendTimes
        when {
          !showingWeekend ->
            ReminderChips(times, onAdd = { pickingFor = WEEKDAYS }) { time ->
              times = formatReminderTimes(parseReminderTimes(times) - time)
              // A tap is easy to make by mistake, and Save keeps it: the time can come back.
              onUndoable("${formatTime(time)} removed.") { times = formatReminderTimes(parseReminderTimes(times) + time) }
            }
          weekend == null -> Text("Same times as weekdays.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          else ->
            ReminderChips(weekend, onAdd = { pickingFor = WEEKEND }) { time ->
              weekendTimes = formatReminderTimes(parseReminderTimes(weekendTimes.orEmpty()) - time)
              onUndoable("${formatTime(time)} removed.") { weekendTimes?.let { weekendTimes = formatReminderTimes(parseReminderTimes(it) + time) } }
            }
        }
        // The list's own action first, then hearing what a reminder sounds like.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
          if (showingWeekend) {
            if (weekend == null) TextAction("Set different times", onClick = { weekendTimes = times })
            else TextAction("Same as weekdays", onClick = { weekendTimes = null })
          }
          TextAction("Preview a reminder", onClick = { viewModel.preview(habit) })
        }
      }
    }
  }

  // Save waits in a bar at the bottom, like the log form's, from the first change on: with two
  // reminder lists the end of the page can be far. Nothing changed, nothing to save, and the page is calm.
  AnimatedVisibility(
    visible = changed,
    enter = expandVertically(tween(280, easing = Motion.EaseUi), expandFrom = Alignment.Top) + fadeIn(tween(160, delayMillis = 60)),
    exit = shrinkVertically(tween(Motion.SHORT, easing = Motion.EaseUi)) + fadeOut(tween(100)),
  ) {
    Column {
      HorizontalDivider()
      Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).gutter().padding(vertical = 12.dp)) {
        Button(
          shape = MaterialTheme.shapes.medium,
          enabled = name.isNotBlank() && !busy,
          modifier = Modifier.fillMaxWidth().height(48.dp),
          onClick = { viewModel.save(habit.copy(name = name.trim(), reminderTimes = times, weekendReminderTimes = weekendTimes, linkedPackage = linkedPackage, icon = icon.name, sessionMinutes = sessionMinutes.takeIf { it != SessionTimer.defaultMinutes(habit.kind) }, certId = certId, examDate = examDay?.let(LocalDate::ofEpochDay))) { onDone("Saved.") } },
        ) {
          Text("Save")
        }
      }
    }
  }
  }

  pickingFor?.let { list ->
    TimeDialog(
      onDismiss = { pickingFor = null },
      onPick = {
        if (list == WEEKEND) weekendTimes = formatReminderTimes(parseReminderTimes(weekendTimes.orEmpty()) + it)
        else times = formatReminderTimes(parseReminderTimes(times) + it)
        pickingFor = null
      },
    )
  }
  if (confirmingBack) {
    AlertDialog(
      onDismissRequest = { confirmingBack = false },
      title = { Text("Discard changes?") },
      text = { Text("The changes to this habit aren't saved.") },
      confirmButton = {
        TextButton(
          onClick = {
            confirmingBack = false
            onDone(null)
          }
        ) {
          Text("Discard")
        }
      },
      dismissButton = { TextButton(onClick = { confirmingBack = false }) { Text("Keep editing") } },
    )
  }
  pickingExamFor?.let { id ->
    val today = viewModel.today()
    DayPickerDialog(
      "Exam day",
      examDay?.let(LocalDate::ofEpochDay)?.takeIf { !it.isBefore(today) },
      earliest = today,
      latest = today.plusYears(1),
      onDismiss = { pickingExamFor = null },
      onPick = { day ->
        certId = id
        examDay = day.toEpochDay()
        pickingExamFor = null
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

private val EXAM_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

private const val WEEKDAYS = "weekdays"
private const val WEEKEND = "weekend"

/** One list of reminder times as chips: a tap takes a time off ([onRemove]), the last chip adds one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderChips(times: String, onAdd: () -> Unit, onRemove: (LocalTime) -> Unit) {
  val parsed = parseReminderTimes(times)
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
      parsed.forEach { time ->
        AssistChip(
          onClick = { onRemove(time) },
          label = { Text(formatTime(time)) },
          trailingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = null, modifier = Modifier.size(16.dp)) },
          modifier = Modifier.semantics { contentDescription = "Remove the ${formatTime(time)} reminder" },
        )
      }
      if (parsed.size < ReminderScheduler.MAX_SLOTS) {
        AssistChip(onClick = onAdd, label = { Text("Add") }, leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp)) }, modifier = Modifier.semantics { contentDescription = "Add a reminder" })
      }
    }
    if (parsed.isEmpty()) Text("No reminders.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (parsed.size > 1) Text("The first opens the day, the last is the last call.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (parsed.size >= ReminderScheduler.MAX_SLOTS) {
      Text("That's the maximum of ${ReminderScheduler.MAX_SLOTS} reminders a day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPicker(selected: HabitIcon, onPick: (HabitIcon) -> Unit) {
  val haptics = LocalHapticFeedback.current
  val colors = MaterialTheme.colorScheme
  FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
          }
          .semantics { contentDescription = icon.label },
        contentAlignment = Alignment.Center,
      ) {
        val tint by animateColorAsState(if (chosen) colors.primary else colors.onSurfaceVariant, tween(Motion.SHORT, easing = Motion.EaseUi), label = "icon tint")
        HabitIconImage(icon, tint = tint)
      }
    }
  }
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
      LazyColumn(Modifier.heightIn(max = 420.dp).selectableGroup()) {
        item { AppRow("None", selected == null) { onPick(null) } }
        items(apps, key = { it.packageName }) { app -> AppRow(app.label, selected == app.packageName) { onPick(app.packageName) } }
      }
    },
  )
}

/** One choice: the whole row is the radio button (one focus stop), like the guard's app list. */
@Composable
private fun AppRow(label: String, selected: Boolean, onClick: () -> Unit) {
  Row(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    // Without its own click the radio is 20dp: padded back to the 48dp it took before.
    RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(14.dp))
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
