package dev.eduarddragu.anotherhabittracker.ui.log

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import dev.eduarddragu.anotherhabittracker.ui.components.Field
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.eduarddragu.anotherhabittracker.domain.Backups
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.data.Entry
import dev.eduarddragu.anotherhabittracker.data.resolvedIcon
import dev.eduarddragu.anotherhabittracker.domain.Books
import dev.eduarddragu.anotherhabittracker.domain.HabitKind
import dev.eduarddragu.anotherhabittracker.domain.SessionTimer
import dev.eduarddragu.anotherhabittracker.domain.Topic
import dev.eduarddragu.anotherhabittracker.domain.TopicPicker
import dev.eduarddragu.anotherhabittracker.reminders.Notifications
import dev.eduarddragu.anotherhabittracker.reminders.Sessions
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
import dev.eduarddragu.anotherhabittracker.ui.components.pressScale
import dev.eduarddragu.anotherhabittracker.ui.components.screenPadding
import dev.eduarddragu.anotherhabittracker.ui.components.touchTarget
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class LogViewModel(app: HabitApp, habitId: Long) : HabitViewModel(app, habitId) {
  /** [finished]: for a reading habit, whether the book was finished with this session; null for other habits. */
  fun save(entry: Entry, finished: Boolean? = null, onDone: () -> Unit) = once {
    val previousStreak = status.value?.streak ?: 0
    if (finished != null) app.repository.saveReading(entry, finished) else app.repository.logSession(entry)
    // The session this log is for is over: the planet goes back to being a planet.
    // Only the finished session this log is for: logging yesterday mid-session keeps the timer.
    app.sessions.session.value?.takeIf { it.habitId == habitId && it.finishedAt != null && it.day == entry.day }?.let { Sessions.clear(app) }
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
  fun update(entry: Entry, finished: Boolean? = null, onDone: () -> Unit) = once {
    if (finished != null) app.repository.saveReading(entry, finished) else app.repository.updateEntry(entry)
    onDone()
  }

  /** Whether the book of a session being edited was finished with it. */
  suspend fun finishedOn(day: LocalDate, title: String): Boolean = app.repository.finishedOn(habitId, day, title)

  fun delete(id: Long, onDone: () -> Unit) = once {
    app.repository.deleteSession(id)
    onDone()
  }

  /**
   * [day]: the form's day, fixed when it opened, so a form left open across midnight freezes that day.
   * [onDone] gets the freeze's id (null if refused), for the undo.
   */
  fun freezeToday(day: LocalDate, onDone: (Long?) -> Unit) = once {
    val previousStreak = status.value?.streak ?: 0
    val id = app.repository.freeze(habitId, day)
    if (id != null) {
      Notifications.dismiss(app, habitId)
      Commits.post(Commit(habitId, day, previousStreak))
    }
    onDone(id)
  }

  /** Undo of [freezeToday]: in the app's scope, since the form is gone by then. */
  fun unfreeze(id: Long) = app.repository.undoFreeze(id)
}

private val EDIT_DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogScreen(
  app: HabitApp,
  habitId: Long,
  onDone: (message: String?) -> Unit,
  /** Closes the form with a message that has an Undo action. */
  onDoneUndoable: (message: String, undo: () -> Unit) -> Unit,
  modifier: Modifier = Modifier,
  /** Set to correct a session already logged instead of logging a new one. */
  entryId: Long? = null,
  /** Set to log a new session for this day (epoch day), fixed: a frozen or missed day, from the habit's page. */
  onDay: Long? = null,
  /** Minutes a finished session counted; shown as its own chip when they aren't a preset. */
  prefillMinutes: Int? = null,
  viewModel: LogViewModel = viewModel(key = "log-$habitId-${entryId ?: "new"}") { LogViewModel(app, habitId) },
) {
  val status by viewModel.status.collectAsStateWithLifecycle()
  val busy by viewModel.busy.collectAsStateWithLifecycle()
  val current = status ?: return
  val habit = current.habit
  val study = habit.kind == HabitKind.STUDY
  // Reading habits name their book (track) and its author (module), and can finish it here.
  val reading = Books.appliesTo(habit.kind, habit.name, habit.icon)
  val haptics = LocalHapticFeedback.current

  // The day is fixed when the form opens: left open across midnight, it still logs for the day it said.
  val openedOn = rememberSaveable { app.repository.today().toEpochDay() }
  val editing = entryId != null
  // A day asked for from the habit's page (yesterday, or a frozen day) is fixed; otherwise it's today.
  // There is no switch in the form: logging for another day starts from that day on the habit's page.
  val fixedDay = onDay?.takeIf { !editing }?.let(LocalDate::ofEpochDay)?.takeIf { it != LocalDate.ofEpochDay(openedOn) }
  var score by rememberSaveable { mutableStateOf<Int?>(null) }
  var minutes by rememberSaveable { mutableStateOf(prefillMinutes) }
  var note by rememberSaveable { mutableStateOf("") }
  var extraTopics by rememberSaveable { mutableStateOf("") }
  // A new reading session starts on the book on the go.
  var track by rememberSaveable { mutableStateOf(current.books?.current?.title.orEmpty().takeIf { reading && !editing }.orEmpty()) }
  var module by rememberSaveable { mutableStateOf(current.books?.current?.author.orEmpty().takeIf { reading && !editing }.orEmpty()) }
  var finished by rememberSaveable { mutableStateOf(false) }
  var details by rememberSaveable { mutableStateOf(false) }
  // A log from a finished focus session takes the topic it was started on: the pick can have moved
  // since (midnight, "keep going", "I know this").
  val sessionTopic = remember { app.sessions.session.value?.takeIf { prefillMinutes != null && !editing && it.habitId == habit.id }?.topicId }
  // Study sessions default to today's pick.
  var topicId by rememberSaveable { mutableStateOf(sessionTopic ?: current.pick?.topic?.id) }
  // Once the topic is changed by hand (or came with the session), the day no longer sets it.
  var topicChosen by rememberSaveable { mutableStateOf(sessionTopic != null) }
  // Editing: the form starts from the session as it was logged.
  var editDay by rememberSaveable { mutableStateOf<Long?>(null) }
  var editLoggedAt by rememberSaveable { mutableStateOf<Long?>(null) }
  var loaded by rememberSaveable { mutableStateOf(!editing) }
  // The note as it was when the form opened (empty for a new session): back asks before losing a
  // different one.
  var savedNote by rememberSaveable { mutableStateOf("") }
  LaunchedEffect(entryId) {
    if (loaded || entryId == null) return@LaunchedEffect
    val entry = viewModel.entry(entryId) ?: return@LaunchedEffect
    editDay = entry.day.toEpochDay()
    editLoggedAt = entry.loggedAt
    score = entry.score
    minutes = entry.minutes
    note = entry.note
    savedNote = entry.note
    extraTopics = entry.extraTopics
    track = entry.track.orEmpty()
    module = entry.module.orEmpty()
    topicId = entry.topicId
    topicChosen = true
    finished = reading && entry.track != null && viewModel.finishedOn(entry.day, entry.track)
    details = entry.extraTopics.isNotBlank() || entry.track != null || entry.module != null
    loaded = true
  }
  var confirmingDelete by rememberSaveable { mutableStateOf(false) }
  var confirmingBack by rememberSaveable { mutableStateOf(false) }
  // Another day's session gets that day's topic; Save waits for it, and a topic picked by hand
  // meanwhile wins.
  var dayTopicLoaded by rememberSaveable { mutableStateOf(!study || editing || fixedDay == null) }
  LaunchedEffect(fixedDay) {
    if (dayTopicLoaded || fixedDay == null) return@LaunchedEffect
    val topic = viewModel.topicOn(fixedDay)
    if (!topicChosen) topicId = topic
    dayTopicLoaded = true
  }
  var pickingTopic by rememberSaveable { mutableStateOf(false) }
  var typingMinutes by rememberSaveable { mutableStateOf(false) }

  // Only a note is worth asking about: scores and minutes are one tap to pick again.
  BackHandler(enabled = loaded && note.isNotBlank() && note.trim() != savedNote.trim() && !busy) { confirmingBack = true }

  val needsScore = study && score == null
  val canSave = !busy && !needsScore && dayTopicLoaded

  // No haptic here: the confirmation lands with the square filling in, on the screen underneath.
  fun save() {
    val today = LocalDate.ofEpochDay(openedOn)
    val day = fixedDay ?: today
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
    // Only a named book can be finished.
    val finishedBook = if (reading) finished && entry.track != null else null
    if (editing) {
      val original = entryId ?: return
      val day = editDay?.let(LocalDate::ofEpochDay) ?: return
      viewModel.update(entry.copy(id = original, day = day, loggedAt = editLoggedAt ?: entry.loggedAt), finishedBook) { onDone("Session updated.") }
      return
    }
    viewModel.save(entry, finishedBook) {
      onDone(
        when {
          day == today -> null
          day == today.minusDays(1) -> "Logged for yesterday."
          else -> "Logged for ${day.format(EDIT_DAY_FORMAT)}."
        }
      )
    }
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
              fixedDay == LocalDate.ofEpochDay(openedOn).minusDays(1) -> "FOR YESTERDAY"
              fixedDay != null -> "FOR " + fixedDay.format(EDIT_DAY_FORMAT).uppercase()
              else -> "FOR TODAY"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).alignByBaseline(),
          )
          if (editing) TextAction("Delete session", onClick = { confirmingDelete = true }, modifier = Modifier.alignByBaseline(), enabled = !busy)
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

      if (reading) {
        // The book leads, prefilled with the one on the go: most days nothing to type. The author
        // comes in once there is a title, and so does finishing it.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Which book?", style = MaterialTheme.typography.titleMedium)
          // The author follows the title while it's one the shelf filled in: a known book brings its
          // own, another title clears it. One typed by hand stays.
          val shelf = current.books
          fun authorOf(title: String) = shelf?.let { listOfNotNull(it.current) + it.finished }?.firstOrNull { Books.key(it.title) == Books.key(title) }?.author.orEmpty()
          Field(
            track,
            { title ->
              if (module == authorOf(track)) module = authorOf(title)
              track = title
            },
            label = "Book", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), singleLine = true, modifier = Modifier.fillMaxWidth())
          AnimatedVisibility(
            visible = track.isNotBlank(),
            enter = expandVertically(tween(280, easing = Motion.EaseUi), expandFrom = Alignment.Top) + fadeIn(tween(160, delayMillis = 60)),
            exit = shrinkVertically(tween(Motion.SHORT, easing = Motion.EaseUi)) + fadeOut(tween(100)),
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Field(module, { module = it }, label = "Author (optional)", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), singleLine = true, modifier = Modifier.fillMaxWidth())
              FilterChip(
                selected = finished,
                onClick = {
                  haptics.performHapticFeedback(if (finished) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                  finished = !finished
                },
                label = { Text("Finished it") },
              )
            }
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("How long?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          // Minutes are optional and picked from presets: a tap selects, a second tap clears.
          // A session's real length stays as it was, before the presets, rather than rounded into one.
          val presets = SessionTimer.presets(habit.kind, habit.sessionMinutes)
          val chips = listOfNotNull(prefillMinutes?.takeIf { it !in presets }) + presets
          chips.forEach { preset ->
            FilterChip(
              selected = minutes == preset,
              onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                minutes = if (minutes == preset) null else preset
              },
              label = { Text("$preset min") },
            )
          }
          // Anything else is typed by hand (a long day, a session edited later); once set, the chip
          // shows it and a tap changes it.
          val custom = minutes?.takeIf { it !in chips }
          // Filled in the accent like Log, chosen or not: the label says which. It opens a dialog rather
          // than toggling, so TalkBack hears a button, not a checkbox.
          val accent = MaterialTheme.colorScheme.primary
          val onAccent = MaterialTheme.colorScheme.onPrimary
          FilterChip(
            selected = custom != null,
            onClick = { typingMinutes = true },
            modifier = Modifier.semantics {
              role = Role.Button
              onClick(label = "Type the minutes") { typingMinutes = true; true }
              if (custom != null) stateDescription = "Selected"
            },
            colors = FilterChipDefaults.filterChipColors(containerColor = accent, labelColor = onAccent, selectedContainerColor = accent, selectedLabelColor = onAccent),
            border = null,
            label = { Text(custom?.let { "$it min" } ?: "Custom") },
          )
        }
      }

      Field(note, { note = it }, label = "Notes", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), minLines = 2, modifier = Modifier.fillMaxWidth())

      if (study) {
        // Optional study details stay folded until asked for.
        Column {
          // The row is shorter than a touch target; its touch area grows without moving it.
          Row(
            Modifier.fillMaxWidth()
              .semantics { stateDescription = if (details) "Expanded" else "Collapsed" }
              .touchTarget(Modifier.clickable(onClickLabel = if (details) "Hide details" else "Show details") { details = !details })
              .padding(vertical = 8.dp),
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
              Field(extraTopics, { extraTopics = it }, label = "Other topics studied", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth())
              Field(track, { track = it }, label = "Course or book", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), singleLine = true, modifier = Modifier.fillMaxWidth())
              Field(module, { module = it }, label = "Module or chapter", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
          }
        }
      }

      if (current.canFreezeToday && !editing && fixedDay == null) {
        TextAction(
          "Freeze today instead (once a week)",
          onClick = { viewModel.freezeToday(LocalDate.ofEpochDay(openedOn)) { id -> if (id != null) onDoneUndoable("Today is frozen.") { viewModel.unfreeze(id) } else onDone("Freeze not available.") } },
          enabled = !busy,
        )
      }
    }

    HorizontalDivider()
    Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).gutter().padding(vertical = 12.dp)) {
      AnimatedVisibility(
        visible = needsScore,
        enter = expandVertically(tween(Motion.SHORT, easing = Motion.EaseUi)) + fadeIn(tween(Motion.SHORT)),
        exit = shrinkVertically(tween(Motion.SHORT, easing = Motion.EaseUi)) + fadeOut(tween(120)),
      ) {
        Text("Pick a score to save.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
      }
      // The button reads back what will be saved.
      val summary =
        listOfNotNull(if (editing) "Save changes" else "Save", score?.takeIf { study }?.let { "$it/5" }, minutes?.let { "$it min" }, "finished".takeIf { reading && finished && track.isNotBlank() }).joinToString(" · ")
      // Waiting for a score it stays warm, so the commit still reads as the way out (and outranks the
      // chips, the Custom one included); only the text goes quiet.
      Button(
        shape = MaterialTheme.shapes.medium,
        enabled = canSave,
        colors = ButtonDefaults.buttonColors(disabledContainerColor = MaterialTheme.colorScheme.primaryContainer, disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier.fillMaxWidth().height(48.dp),
        onClick = ::save,
      ) {
        Text(summary)
      }
    }
  }

  if (confirmingDelete && entryId != null) {
    AlertDialog(
      onDismissRequest = { confirmingDelete = false },
      title = { Text("Delete this session?") },
      text = { Text(if (reading) "It comes off the history, the streak and the book's time." else "It comes off the history, the streak and the topic's schedule.") },
      confirmButton = {
        TextButton(
          onClick = {
            confirmingDelete = false
            viewModel.delete(entryId) { onDone("Session deleted.") }
          }
        ) {
          Text("Delete")
        }
      },
      dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
    )
  }

  if (typingMinutes) {
    CustomMinutesDialog(
      // A preset stays out of the field: the dialog only edits what it set.
      initial = minutes?.takeIf { it !in listOfNotNull(prefillMinutes) + SessionTimer.presets(habit.kind, habit.sessionMinutes) },
      onSet = {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        minutes = it
        typingMinutes = false
      },
      onDismiss = { typingMinutes = false },
    )
  }

  if (confirmingBack) {
    AlertDialog(
      onDismissRequest = { confirmingBack = false },
      title = { Text("Discard the note?") },
      text = { Text("It isn't saved yet.") },
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
 * the square that fills in later. The chosen one fills in; a press dips it like any control.
 */
@Composable
private fun ScoreSquares(selected: Int?, onPick: (Int) -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    (1..5).forEach { value ->
      val chosen = selected == value
      // Score to heatmap level: 1-2 -> 1, 3 -> 2, 4 -> 3, 5 -> 4.
      val level = when (value) { 1, 2 -> 1; 3 -> 2; 4 -> 3; else -> 4 }
      val target = if (chosen) colors.primary.copy(alpha = LEVEL_ALPHA[level]) else colors.surfaceContainerLow
      val fill by animateColorAsState(target, tween(if (chosen) 180 else 120, easing = Motion.EaseUi), label = "score")
      // Pressed, it dips like every other control; chosen, only the fill says so (no jump).
      val interaction = remember { MutableInteractionSource() }
      val scale = pressScale(interaction)
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
          .selectable(chosen, interactionSource = interaction, indication = null, role = Role.RadioButton) { onPick(value) }
          // "1 of 5, didn't get it": the ends say what they mean, as the labels under the row do.
          .semantics { contentDescription = listOfNotNull("$value of 5", TopicPicker.scoreLabel(value).lowercase().takeIf { value == 1 || value == 5 }).joinToString(", ") },
        contentAlignment = Alignment.Center,
      ) {
        Text(value.toString(), style = NumeralsSmall, color = if (strong) colors.onPrimary else colors.onSurface, modifier = Modifier.clearAndSetSemantics {})
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
        Field(query, { query = it }, label = "Search", singleLine = true, modifier = Modifier.fillMaxWidth())
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

/** Minutes typed by hand: whole minutes up to a day; an empty field clears them. */
@Composable
private fun CustomMinutesDialog(initial: Int?, onSet: (Int?) -> Unit, onDismiss: () -> Unit) {
  var text by rememberSaveable { mutableStateOf(initial?.toString().orEmpty()) }
  val value = SessionTimer.customMinutes(text)
  // An empty field only clears a value the dialog set; with nothing to clear there is nothing to set.
  val valid = if (text.isBlank()) initial != null else value != null
  val focus = remember { FocusRequester() }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("How many minutes?") },
    text = {
      // Asked from inside the dialog's window, once the field is in it.
      LaunchedEffect(Unit) { focus.requestFocus() }
      Field(
        text,
        { typed -> text = typed.filter(Char::isDigit).take(4) },
        label = "Minutes",
        suffix = { Text("min") },
        isError = text.isNotBlank() && value == null,
        supportingText = { Text("From 1 to ${Backups.MAX_MINUTES}, a whole day.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (valid) onSet(value) }),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
      )
    },
    confirmButton = { TextButton(onClick = { onSet(value) }, enabled = valid) { Text(if (text.isBlank() && initial != null) "Clear" else "Set") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
