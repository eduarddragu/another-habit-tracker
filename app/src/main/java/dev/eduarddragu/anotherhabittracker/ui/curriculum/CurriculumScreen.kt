package dev.eduarddragu.anotherhabittracker.ui.curriculum

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.data.resolvedIcon
import dev.eduarddragu.anotherhabittracker.domain.Topic
import dev.eduarddragu.anotherhabittracker.domain.TopicPicker
import dev.eduarddragu.anotherhabittracker.domain.TopicState
import dev.eduarddragu.anotherhabittracker.theme.Motion
import dev.eduarddragu.anotherhabittracker.theme.NumeralsDisplay
import dev.eduarddragu.anotherhabittracker.theme.NumeralsSmall
import dev.eduarddragu.anotherhabittracker.ui.components.Chevron
import dev.eduarddragu.anotherhabittracker.ui.components.HabitViewModel
import dev.eduarddragu.anotherhabittracker.ui.components.ScreenTitle
import dev.eduarddragu.anotherhabittracker.ui.components.SectionLabel
import dev.eduarddragu.anotherhabittracker.ui.components.TextAction
import dev.eduarddragu.anotherhabittracker.ui.components.launchSafely
import dev.eduarddragu.anotherhabittracker.ui.components.opticalStart
import dev.eduarddragu.anotherhabittracker.ui.components.rememberArrival
import dev.eduarddragu.anotherhabittracker.ui.components.rise
import dev.eduarddragu.anotherhabittracker.ui.components.screenPadding

class CurriculumViewModel(app: HabitApp, habitId: Long) : HabitViewModel(app, habitId) {
  // Marking is idempotent, so no double-tap guard: tapping several topics quickly must all register.
  fun markKnown(topicIds: Collection<String>, onMarked: (Set<String>) -> Unit = {}) = launchSafely { onMarked(app.repository.markKnown(habitId, topicIds)) }

  fun unmark(topicIds: Set<String>) = app.repository.undoKnown(habitId, topicIds)

  fun unmarkKnown(topicId: String) = launchSafely { app.repository.unmarkKnown(habitId, topicId) }

  /** Makes [topicId] today's topic (null goes back to the picker's). */
  fun studyToday(topicId: String?) = app.repository.keepGoing(habitId, topicId)
}

private val COVERED = setOf(TopicState.DONE, TopicState.KNOWN)

/** Due reviews listed on the page; the rest are counted. */
private const val MAX_DUE = 5

/** "SCORED 2 · DUE TODAY", "SCORED 4 · DUE 12 DAYS AGO". */
private fun dueLine(review: TopicPicker.DueReview, today: java.time.LocalDate): String {
  val days = today.toEpochDay() - review.due.toEpochDay()
  val due = when (days) { 0L -> "DUE TODAY"; 1L -> "DUE YESTERDAY"; else -> "DUE $days DAYS AGO" }
  return "SCORED ${review.score} · $due"
}

/** Areas and topics with their state; the place to mark what is already known. */
@Composable
fun CurriculumScreen(
  app: HabitApp,
  habitId: Long,
  onUndoable: (String, () -> Unit) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: CurriculumViewModel = viewModel(key = "curriculum-$habitId") { CurriculumViewModel(app, habitId) },
) {
  val state by viewModel.status.collectAsStateWithLifecycle()
  val status = state ?: return
  val curriculum = viewModel.curriculum
  val marks = status.topicMarks
  var expanded by rememberSaveable { mutableStateOf<String?>(null) }
  // The header, then the areas, arriving as the page slides in.
  val arrival = rememberArrival(2)

  LazyColumn(modifier, contentPadding = screenPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    item {
      val covered = curriculum.topics.count { TopicPicker.state(it, marks) in COVERED }
      Column(Modifier.rise(arrival[0], 16.dp)) {
        ScreenTitle("Curriculum", "What you know", icon = status.habit.resolvedIcon)
        Spacer(Modifier.height(24.dp))
        Coverage(covered, curriculum.topics.size)
        Text(
          "Mark what you already know, so the picker doesn't start from first principles.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
      }
    }
    // Reviews waiting, weakest first: the picker brings one back now and then, this lists them all
    // and lets one be today's topic.
    val due = TopicPicker.dueReviews(curriculum, marks.values.toList(), status.today)
    if (due.isNotEmpty()) {
      item(key = "due") {
        Column(Modifier.rise(arrival[1], 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          SectionLabel("Due for review · ${due.size}")
          due.take(MAX_DUE).forEach { review ->
            val today = status.pick?.topic?.id == review.topic.id
            Row(Modifier.fillMaxWidth()) {
              Column(Modifier.weight(1f).alignByBaseline()) {
                Text(review.topic.title, style = MaterialTheme.typography.titleMedium)
                Text(dueLine(review, status.today), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              if (today) {
                Text("TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.alignByBaseline())
              } else if (!status.doneToday) {
                val previous = status.pick?.topic?.id.takeIf { status.focused }
                TextAction(
                  "Study today",
                  onClick = {
                    viewModel.studyToday(review.topic.id)
                    onUndoable("Today's topic changed") { viewModel.studyToday(previous) }
                  },
                  modifier = Modifier.alignByBaseline(),
                )
              }
            }
          }
          if (due.size > MAX_DUE) Text("And ${due.size - MAX_DUE} more.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    curriculum.areas.forEach { area ->
      val topics = curriculum.topics.filter { it.area == area.id }
      val covered = topics.count { TopicPicker.state(it, marks) in COVERED }
      val open = expanded == area.id
      item(key = "area-${area.id}") {
        Column(Modifier.rise(arrival[1], 16.dp).animateItem(placementSpec = tween(Motion.LIST, easing = Motion.EaseUi))) {
          HorizontalDivider()
          // Name and count share a baseline; the chevron centres on the row.
          Row(
            Modifier.fillMaxWidth()
              .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
              .clickable(onClickLabel = if (open) "Hide topics" else "Show topics") { expanded = if (open) null else area.id }
              .padding(vertical = 12.dp)
          ) {
            Text(area.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).alignByBaseline())
            // Fractions read the same everywhere: DM Sans, no spaces.
            Text("$covered/${topics.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alignByBaseline())
            Chevron(open, Modifier.align(Alignment.CenterVertically).padding(start = 12.dp))
          }
        }
      }
      if (open) {
        item(key = "all-${area.id}") {
          TextAction(
            "Mark the whole area as known",
            // One tap marks a whole area: easy to regret, so it comes with an undo.
            onClick = { viewModel.markKnown(topics.map { it.id }) { marked -> if (marked.isNotEmpty()) onUndoable("Marked ${marked.size} as known") { viewModel.unmark(marked) } } },
            modifier = Modifier.animateItem(fadeInSpec = tween(Motion.SHORT), placementSpec = tween(Motion.LIST, easing = Motion.EaseUi), fadeOutSpec = tween(120)),
          )
        }
        items(topics, key = { it.id }) { topic ->
          TopicRow(
            topic,
            TopicPicker.state(topic, marks),
            onKnown = { viewModel.markKnown(listOf(topic.id)) },
            onUnknown = { viewModel.unmarkKnown(topic.id) },
            modifier = Modifier.animateItem(fadeInSpec = tween(Motion.SHORT), placementSpec = tween(Motion.LIST, easing = Motion.EaseUi), fadeOutSpec = tween(120)),
          )
        }
      }
    }
  }
}

/** "12/122 TOPICS COVERED" in numerals, over a hairline bar that fills with the accent. */
@Composable
private fun Coverage(covered: Int, total: Int) {
  val colors = MaterialTheme.colorScheme
  val share by animateFloatAsState(if (total == 0) 0f else covered.toFloat() / total, tween(Motion.LONG, easing = Motion.EaseUi), label = "coverage")
  // Read as one value with its bar, like the streak, instead of four fragments.
  Column(Modifier.clearAndSetSemantics { contentDescription = "$covered of $total topics covered"; progressBarRangeInfo = ProgressBarRangeInfo(if (total == 0) 0f else covered.toFloat() / total, 0f..1f) }) {
    // One baseline for the number, the total and the caption's last line, like the streak.
    Row {
      Text(covered.toString(), style = NumeralsDisplay, color = if (covered > 0) colors.primary else colors.onSurfaceVariant, modifier = Modifier.opticalStart(covered.toString(), NumeralsDisplay).alignBy(LastBaseline))
      Text("/$total", style = NumeralsSmall, color = colors.onSurfaceVariant, modifier = Modifier.alignBy(LastBaseline))
      Spacer(Modifier.width(12.dp))
      // The same caption as the streak: the unit in the accent, the rest muted.
      Column(Modifier.alignBy(LastBaseline)) {
        Text("TOPICS", style = MaterialTheme.typography.labelMedium, color = colors.primary)
        Text("COVERED", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
      }
    }
    Spacer(Modifier.height(12.dp))
    Spacer(
      Modifier.fillMaxWidth().height(4.dp).drawBehind {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(colors.outlineVariant, cornerRadius = radius)
        if (share > 0f) drawRoundRect(colors.primary, size = Size(size.width * share, size.height), cornerRadius = radius)
      }
    )
  }
}

@Composable
private fun TopicRow(topic: Topic, state: TopicState, onKnown: () -> Unit, onUnknown: () -> Unit, modifier: Modifier = Modifier) {
  // Same left edge as the area titles; a little air between rows so a state label never touches the next
  // title. The action sits on the title's first line, not between its lines.
  Row(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
    Column(Modifier.weight(1f).alignBy(FirstBaseline)) {
      Text(topic.title, style = MaterialTheme.typography.bodyLarge)
      // Locked is the default; only the states that say something get a label.
      stateLabel(state)?.let { label ->
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (state == TopicState.WEAK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    when (state) {
      TopicState.KNOWN -> TextAction("Not known", onClick = onUnknown, modifier = Modifier.padding(start = 16.dp).alignBy(FirstBaseline).semantics { contentDescription = "${topic.title}: not known after all" }, vertical = 0.dp)
      TopicState.DONE -> {}
      else -> TextAction("Known", onClick = onKnown, modifier = Modifier.padding(start = 16.dp).alignBy(FirstBaseline).semantics { contentDescription = "Mark ${topic.title} as known" }, vertical = 0.dp)
    }
  }
}

private fun stateLabel(state: TopicState): String? =
  when (state) {
    TopicState.LOCKED -> null
    TopicState.AVAILABLE -> "UP NEXT"
    TopicState.WEAK -> "SHAKY"
    TopicState.DONE -> "COVERED"
    TopicState.KNOWN -> "KNOWN"
  }
