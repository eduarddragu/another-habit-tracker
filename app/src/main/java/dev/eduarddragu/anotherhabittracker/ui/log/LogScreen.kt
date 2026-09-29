package dev.eduarddragu.anotherhabittracker.ui.log

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.data.Entry
import dev.eduarddragu.anotherhabittracker.data.resolvedIcon
import dev.eduarddragu.anotherhabittracker.domain.HabitKind
import dev.eduarddragu.anotherhabittracker.domain.LogDefaults
import dev.eduarddragu.anotherhabittracker.domain.Topic
import dev.eduarddragu.anotherhabittracker.domain.TopicPicker
import dev.eduarddragu.anotherhabittracker.reminders.Notifications
import dev.eduarddragu.anotherhabittracker.theme.Motion
import dev.eduarddragu.anotherhabittracker.theme.NumeralsSmall
import dev.eduarddragu.anotherhabittracker.theme.fadeThrough
import dev.eduarddragu.anotherhabittracker.ui.components.Chevron
import dev.eduarddragu.anotherhabittracker.ui.components.Commit
import dev.eduarddragu.anotherhabittracker.ui.components.Commits
import dev.eduarddragu.anotherhabittracker.ui.components.HabitViewModel
import dev.eduarddragu.anotherhabittracker.ui.components.LEVEL_ALPHA
import dev.eduarddragu.anotherhabittracker.ui.components.ScreenTitle
import dev.eduarddragu.anotherhabittracker.ui.components.SectionLabel
import dev.eduarddragu.anotherhabittracker.ui.components.TextAction
import dev.eduarddragu.anotherhabittracker.ui.components.gutter
import dev.eduarddragu.anotherhabittracker.ui.components.screenPadding
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class LogViewModel(app: HabitApp, habitId: Long) : HabitViewModel(app, habitId) {
  fun save(entry: Entry, onDone: () -> Unit) = once {
    val previousStreak = status.value?.streak ?: 0
    app.repository.logSession(entry)
    if (entry.day == app.repository.today()) Notifications.dismiss(app, habitId)
    // The write succeeded: the screen underneath plays it.
    Commits.post(Commit(habitId, entry.day, previousStreak))
    onDone()
  }

  /**
   * The topic shown on [day], so a session logged for yesterday gets yesterday's topic: from the
   * history if the app saw that day, otherwise worked out again from the log.
   */
  suspend fun topicOn(day: LocalDate): String? = app.pickHistory.topicOn(habitId, day) ?: app.repository.pickOn(habitId, day)?.topic?.id

  suspend fun entry(id: Long): Entry? = app.repository.entry(id)

  /** A logged session corrected: same id and day, new values. No commit to play, it's not new. */
  fun update(entry: Entry, onDone: () -> Unit) = once {
    app.repository.updateEntry(entry)
    onDone()
  }

  fun delete(id: Long, onDone: () -> Unit) = once {
    app.repository.deleteEntry(id)
    onDone()
  }

  fun freezeToday(onDone: (Boolean) -> Unit) = once {
    val previousStreak = status.value?.streak ?: 0
    val today = app.repository.today()
    val ok = app.repository.freeze(habitId, today)
    if (ok) {
      Notifications.dismiss(app, habitId)
      Commits.post(Commit(habitId, today, previousStreak, frozen = true))
    }
    onDone(ok)
  }
}

private val EDIT_DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

// Minutes are optional and picked from presets only: a tap selects, a second tap clears.
private val STUDY_MINUTES = listOf(30, 45, 60)
private val SIMPLE_MINUTES = listOf(5, 10, 15)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogScreen(
  app: HabitApp,
  habitId: Long,
  onDone: (message: String?) -> Unit,
  modifier: Modifier = Modifier,
  /** Set to correct a session already logged instead of logging a new one. */
  entryId: Long? = null,
  viewModel: LogViewModel = viewModel(key = "log-$habitId-${entryId ?: "new"}") { LogViewModel(app, habitId) },
) {
  val status by viewModel.status.collectAsStateWithLifecycle()
  val busy by viewModel.busy.collectAsStateWithLifecycle()
  val current = status ?: return
  val habit = current.habit
  val study = habit.kind == HabitKind.STUDY
  val haptics = LocalHapticFeedback.current

  // The day is fixed when the form opens: left open across midnight, it still logs for the day it said.
  val openedOn = rememberSaveable { app.repository.today().toEpochDay() }
  val editing = entryId != null
  // In the small hours with yesterday still empty, the session is almost certainly yesterday's.
  var yesterday by rememberSaveable {
    mutableStateOf(!editing && LogDefaults.startOnYesterday(LocalTime.now(), yesterdayLogged = current.cells[LocalDate.ofEpochDay(openedOn).minusDays(1)] != null))
  }
  var score by rememberSaveable { mutableStateOf<Int?>(null) }
  var minutes by rememberSaveable { mutableStateOf<Int?>(null) }
  var note by rememberSaveable { mutableStateOf("") }
  var extraTopics by rememberSaveable { mutableStateOf("") }
  var track by rememberSaveable { mutableStateOf("") }
  var module by rememberSaveable { mutableStateOf("") }
  var details by rememberSaveable { mutableStateOf(false) }
  // Study sessions default to today's pick.
  var topicId by rememberSaveable { mutableStateOf(current.pick?.topic?.id) }
  // Until the topic is changed by hand, it follows the day: "Yesterday" switches it to yesterday's pick.
  var topicChosen by rememberSaveable { mutableStateOf(false) }
  // Editing: the form starts from the session as it was logged.
  var editDay by rememberSaveable { mutableStateOf<Long?>(null) }
  var editLoggedAt by rememberSaveable { mutableStateOf<Long?>(null) }
  var loaded by rememberSaveable { mutableStateOf(!editing) }
  LaunchedEffect(entryId) {
    if (loaded || entryId == null) return@LaunchedEffect
    val entry = viewModel.entry(entryId) ?: return@LaunchedEffect
    editDay = entry.day.toEpochDay()
    editLoggedAt = entry.loggedAt
    score = entry.score
    minutes = entry.minutes
    note = entry.note
    extraTopics = entry.extraTopics
    track = entry.track.orEmpty()
    module = entry.module.orEmpty()
    topicId = entry.topicId
    topicChosen = true
    details = entry.extraTopics.isNotBlank() || entry.track != null || entry.module != null
    loaded = true
  }
  var confirmingDelete by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(yesterday) {
    if (!study || topicChosen || editing) return@LaunchedEffect
    val day = LocalDate.ofEpochDay(openedOn)
    topicId = if (yesterday) viewModel.topicOn(day.minusDays(1)) else current.pick?.topic?.id
  }
  var pickingTopic by rememberSaveable { mutableStateOf(false) }

  val needsScore = study && score == null
  val canSave = !busy && !needsScore

  // No haptic here: the confirmation lands with the square filling in, on the screen underneath.
  fun save() {
    val day = LocalDate.ofEpochDay(openedOn).let { if (yesterday) it.minusDays(1) else it }
    val entry =
      Entry(
        habitId = habit.id,
        day = day,
        score = if (study) score else null,
        minutes = minutes,
        note = note.trim(),
        extraTopics = extraTopics.trim(),
        track = track.trim().ifEmpty { null },
        module = module.trim().ifEmpty { null },
        topicId = if (study) topicId else null,
      )
    if (editing) {
      val original = entryId ?: return
      val day = editDay?.let(LocalDate::ofEpochDay) ?: return
      viewModel.update(entry.copy(id = original, day = day, loggedAt = editLoggedAt ?: entry.loggedAt)) { onDone("Session updated") }
      return
    }
    viewModel.save(entry) { onDone(if (yesterday) "Logged for yesterday" else null) }
  }

  Column(modifier) {
    // The form scrolls under the status bar; the Save bar below stays above the gesture bar and the keyboard.
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(screenPadding(bottom = 16.dp)), verticalArrangement = Arrangement.spacedBy(24.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenTitle(habit.name, if (editing) "Edit session" else "How did it go?", icon = habit.resolvedIcon)
        // Mono label and text action on one baseline.
        Row {
          Text(
            when {
              editing -> "FOR " + (editDay?.let { LocalDate.ofEpochDay(it).format(EDIT_DAY_FORMAT).uppercase() } ?: "")
              yesterday -> "FOR YESTERDAY"
              else -> "FOR TODAY"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).alignByBaseline(),
          )
          if (editing) TextAction("Delete session", onClick = { confirmingDelete = true }, modifier = Modifier.alignByBaseline(), enabled = !busy)
          else TextAction(if (yesterday) "Today instead" else "Yesterday", onClick = { yesterday = !yesterday }, modifier = Modifier.alignByBaseline())
        }
      }

      if (study) {
        val topic = topicId?.let { viewModel.curriculum.byId[it] }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            SectionLabel("Topic")
            Text(topic?.title ?: "None", style = MaterialTheme.typography.titleSmall)
          }
          TextAction("Change", onClick = { pickingTopic = true })
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("How well did it stick?", style = MaterialTheme.typography.titleMedium)
          ScoreSquares(score) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            score = it
          }
          // Once a score is picked, what it does to the topic: the picker's rules in one line.
          AnimatedContent(targetState = score, transitionSpec = { fadeThrough() }, label = "score meaning") { picked ->
            if (picked == null) {
              Row {
                Text("DIDN'T GET IT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("COULD TEACH IT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            } else {
              Text(TopicPicker.scoreMeaning(picked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("How long?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          (if (study) STUDY_MINUTES else SIMPLE_MINUTES).forEach { preset ->
            FilterChip(
              selected = minutes == preset,
              onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                minutes = if (minutes == preset) null else preset
              },
              label = { Text("$preset min") },
            )
          }
        }
      }

      OutlinedTextField(note, { note = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())

      if (study) {
        // Optional study details stay folded until asked for.
        Column {
          Row(
            Modifier.fillMaxWidth().clickable { details = !details }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Add details", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Chevron(details)
          }
          AnimatedVisibility(
            visible = details,
            enter = expandVertically(tween(280, easing = Motion.EaseUi), expandFrom = Alignment.Top) + fadeIn(tween(160, delayMillis = 60)),
            exit = shrinkVertically(tween(Motion.SHORT, easing = Motion.EaseUi)) + fadeOut(tween(100)),
          ) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              OutlinedTextField(extraTopics, { extraTopics = it }, label = { Text("Other topics studied") }, modifier = Modifier.fillMaxWidth())
              OutlinedTextField(track, { track = it }, label = { Text("Course or book") }, singleLine = true, modifier = Modifier.fillMaxWidth())
              OutlinedTextField(module, { module = it }, label = { Text("Module or chapter") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
          }
        }
      }

      if (current.canFreezeToday && !yesterday && !editing) {
        TextAction("Freeze today instead (once a week)", onClick = { viewModel.freezeToday { ok -> onDone(if (ok) null else "Freeze not available") } }, enabled = !busy)
      }
    }

    HorizontalDivider()
    Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).gutter().padding(vertical = 12.dp)) {
      AnimatedVisibility(
        visible = needsScore,
        enter = expandVertically(tween(Motion.SHORT, easing = Motion.EaseUi)) + fadeIn(tween(Motion.SHORT)),
        exit = shrinkVertically(tween(Motion.SHORT, easing = Motion.EaseUi)) + fadeOut(tween(120)),
      ) {
        Text("Pick a score to save", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
      }
      // The button reads back what will be saved.
      val summary = listOfNotNull(if (editing) "Save changes" else "Save", score?.takeIf { study }?.let { "$it/5" }, minutes?.let { "$it min" }).joinToString(" · ")
      Button(enabled = canSave, modifier = Modifier.fillMaxWidth(), onClick = ::save) { Text(summary) }
    }
  }

  if (confirmingDelete && entryId != null) {
    AlertDialog(
      onDismissRequest = { confirmingDelete = false },
      title = { Text("Delete this session?") },
      text = { Text("It comes off the history, the streak and the topic's schedule.") },
      confirmButton = {
        TextButton(
          onClick = {
            confirmingDelete = false
            viewModel.delete(entryId) { onDone("Session deleted") }
          }
        ) {
          Text("Delete")
        }
      },
      dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
    )
  }

  if (pickingTopic) {
    TopicDialog(
      viewModel.curriculum.topics,
      onDismiss = { pickingTopic = false },
      onPick = {
        topicId = it
        topicChosen = true
        pickingTopic = false
      },
    )
  }
}

/**
 * Five squares, 1 to 5, each in the heatmap colour its score will leave: the square tapped here is
 * the square that fills in later. The chosen one fills and settles from 0.9 to full size.
 */
@Composable
private fun ScoreSquares(selected: Int?, onPick: (Int) -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    (1..5).forEach { value ->
      val chosen = selected == value
      // Score to heatmap level: 1-2 -> 1, 3 -> 2, 4 -> 3, 5 -> 4.
      val level = when (value) { 1, 2 -> 1; 3 -> 2; 4 -> 3; else -> 4 }
      val target = if (chosen) colors.primary.copy(alpha = LEVEL_ALPHA[level]) else colors.surfaceContainerLow
      val fill by animateColorAsState(target, tween(if (chosen) 180 else 120, easing = Motion.EaseUi), label = "score")
      val scale = remember { Animatable(1f) }
      LaunchedEffect(chosen) {
        if (chosen) {
          scale.snapTo(0.9f)
          scale.animateTo(1f, tween(180, easing = Motion.EaseUi))
        }
      }
      val strong = chosen && level >= 3
      Box(
        Modifier.weight(1f)
          .aspectRatio(1f)
          .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
          }
          .clip(MaterialTheme.shapes.medium)
          .drawBehind { drawRect(fill) }
          .selectable(chosen, role = Role.RadioButton) { onPick(value) },
        contentAlignment = Alignment.Center,
      ) {
        Text(value.toString(), style = NumeralsSmall, color = if (strong) colors.onPrimary else colors.onSurface)
      }
    }
  }
}

@Composable
private fun TopicDialog(topics: List<Topic>, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
  var query by rememberSaveable { mutableStateOf("") }
  val shown = topics.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true) }
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    title = { Text("Topic") },
    text = {
      Column {
        OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.heightIn(max = 400.dp)) {
          item { Text("None", Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 10.dp)) }
          items(shown, key = { it.id }) { topic ->
            Text(topic.title, Modifier.fillMaxWidth().clickable { onPick(topic.id) }.padding(vertical = 10.dp))
          }
        }
      }
    },
  )
}
