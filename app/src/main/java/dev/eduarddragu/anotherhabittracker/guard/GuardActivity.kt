package dev.eduarddragu.anotherhabittracker.guard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.MainActivity
import dev.eduarddragu.anotherhabittracker.data.HabitStatus
import dev.eduarddragu.anotherhabittracker.domain.GuardText
import dev.eduarddragu.anotherhabittracker.domain.HabitKind
import dev.eduarddragu.anotherhabittracker.domain.ScrollGuard
import dev.eduarddragu.anotherhabittracker.reminders.Notifications
import dev.eduarddragu.anotherhabittracker.reminders.resolveLinkedApp
import dev.eduarddragu.anotherhabittracker.theme.AnotherHabitTrackerTheme
import dev.eduarddragu.anotherhabittracker.theme.Motion
import dev.eduarddragu.anotherhabittracker.theme.NumeralsDisplay
import dev.eduarddragu.anotherhabittracker.ui.components.DayCard
import dev.eduarddragu.anotherhabittracker.ui.components.TextAction
import dev.eduarddragu.anotherhabittracker.ui.components.rememberArrival
import dev.eduarddragu.anotherhabittracker.ui.components.rise
import dev.eduarddragu.anotherhabittracker.ui.components.screenPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The scroll guard's screen, opened by [ScrollGuardService] on top of a guarded app: what's still open
 * today and what's at stake, a way to go do it, and a way to let the feed through for a few minutes.
 * Back leaves the guarded app for the home screen: backing out means giving up the scroll, not
 * getting past the guard. A preview (from the guard's settings) shows the same screen and saves nothing.
 */
class GuardActivity : ComponentActivity() {
  private var content by mutableStateOf<GuardContent?>(null)
  private var preview = false
  /** This appearance was already counted in the week's stats (a recreated activity, or a redraw). */
  private var counted = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    counted = savedInstanceState != null
    enableEdgeToEdge()
    load(intent)
    setContent {
      AnotherHabitTrackerTheme {
        // The Surface sets the theme's content color: without it, text with no explicit
        // color stays black, which vanished on the dark background.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          content?.let { GuardScreen(it, onDoIt = ::doIt, onGrant = ::grant, onLeave = ::leave) }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    counted = false
    load(intent)
  }

  private fun load(intent: Intent) {
    val app = application as HabitApp
    val pkg = intent.getStringExtra(EXTRA_PACKAGE)
    preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
    lifecycleScope.launch {
      // Computed now: the shared flow can still hold yesterday's statuses just after midnight.
      val statuses = app.repository.statuses()
      // A preview with everything done still shows something: the screen as it would be this morning.
      val open = statuses.filter { it.summary.dayOpen }.ifEmpty { if (preview) statuses else emptyList() }
      // Logged in the meantime (from the widget, say): nothing left to guard.
      if (open.isEmpty()) return@launch finish()
      val today = app.repository.today()
      val budget = app.guard.budget(today)
      val appName = resolveLinkedApp(this@GuardActivity, pkg)?.label ?: "The feed"
      val stakes =
        ScrollGuard.Stakes(
          names = open.map { it.habit.name },
          studyOpen = open.any { it.habit.kind == HabitKind.STUDY },
          anythingLogged = statuses.any { it.doneToday },
          // The guard talks about studying, so the streak at stake is study's when it's open;
          // otherwise the longest one still open.
          streak = (open.firstOrNull { it.habit.kind == HabitKind.STUDY } ?: open.maxBy { it.streak }).streak,
        )
      val text = ScrollGuard.text(ScrollGuard.moment(budget), stakes, appName, today, budget.grants)
      // Counted once per time it comes up for real (not a preview, not a redraw of the same screen).
      if (!preview && !counted) {
        app.guard.recordBlock(today)
        counted = true
      }
      content = GuardContent(appName, text, stakes.streak, open, ScrollGuard.grantLabel(budget), waitSeconds = if (budget.grants >= 1) GRANT_WAIT_SECONDS else 0)
    }
  }

  /** Straight to the habit: a study habit's page (where the topic is), another habit's log form. */
  private fun doIt() {
    val open = content?.open.orEmpty()
    val target = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    when (val habit = open.singleOrNull()?.habit) {
      null -> target.putExtra(Notifications.EXTRA_OPEN_HOME, true)
      else -> target.putExtra(if (habit.kind == HabitKind.STUDY) Notifications.EXTRA_OPEN_HABIT_ID else Notifications.EXTRA_LOG_HABIT_ID, habit.id)
    }
    startActivity(target)
    finish()
  }

  /** Lets the feed through; the service starts counting as soon as the grant is saved. */
  private fun grant() {
    val app = application as HabitApp
    if (!preview) app.guard.grant(app.repository.today())
    finish()
  }

  private fun leave() {
    if (!preview) startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    finish()
  }

  companion object {
    private const val EXTRA_PACKAGE = "package"
    private const val EXTRA_PREVIEW = "preview"

    /** After the first grant of the day, the next ones take a few seconds to become tappable. */
    private const val GRANT_WAIT_SECONDS = 3

    fun intent(context: Context, pkg: String, preview: Boolean = false): Intent =
      Intent(context, GuardActivity::class.java)
        .putExtra(EXTRA_PACKAGE, pkg)
        .putExtra(EXTRA_PREVIEW, preview)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
  }
}

private data class GuardContent(val appName: String, val text: GuardText, val streak: Int, val open: List<HabitStatus>, val grantLabel: String, val waitSeconds: Int)

/** "Start Study" opens the topic, "Log Meditation" the log form; with more open, Home. */
private fun doItLabel(open: List<HabitStatus>): String {
  val habit = open.singleOrNull()?.habit ?: return "Show me what's open"
  return if (habit.kind == HabitKind.STUDY) "Start ${habit.name}" else "Log ${habit.name}"
}

@Composable
private fun GuardScreen(content: GuardContent, onDoIt: () -> Unit, onGrant: () -> Unit, onLeave: () -> Unit) {
  BackHandler(onBack = onLeave)
  val arrival = rememberArrival(4, delayOf = { Motion.STAGGER * it.toLong() }, durationOf = { Motion.ENTRANCE })
  val colors = MaterialTheme.colorScheme
  val topic = content.open.firstNotNullOfOrNull { it.pick?.topic }
  var wait by remember(content) { mutableIntStateOf(content.waitSeconds) }
  LaunchedEffect(content) {
    while (wait > 0) {
      delay(1_000)
      wait--
    }
  }
  // What to say at the top, where the eye lands; what to do at the bottom, where the thumb is.
  Column(Modifier.fillMaxSize().background(colors.background).padding(screenPadding(top = 48.dp, bottom = 24.dp))) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
      Column(Modifier.rise(arrival[0], 16.dp)) {
        // Muted, so the streak is the one warm thing above the button.
        Text(content.appName.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(content.text.title, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(content.text.body, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
      }
      if (content.streak > 0) {
        Row(Modifier.rise(arrival[1], 16.dp)) {
          Text(content.streak.toString(), style = NumeralsDisplay, color = colors.primary, modifier = Modifier.alignByBaseline())
          Spacer(Modifier.width(12.dp))
          Text(if (content.streak == 1) "DAY, GONE AT MIDNIGHT" else "DAYS, GONE AT MIDNIGHT", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.alignByBaseline())
        }
      }
      if (topic != null) {
        DayCard(done = false, modifier = Modifier.rise(arrival[2], 16.dp)) {
          Text("TODAY'S TOPIC", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
          Text(topic.title, style = MaterialTheme.typography.titleLarge)
        }
      }
    }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp).rise(arrival[3], 16.dp)) {
      Button(shape = MaterialTheme.shapes.medium, onClick = onDoIt, modifier = Modifier.fillMaxWidth()) { Text(doItLabel(content.open)) }
      Spacer(Modifier.height(4.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextAction("Close ${content.appName}", onClick = onLeave, color = colors.onSurfaceVariant)
        TextAction(if (wait > 0) "${content.grantLabel} ($wait)" else content.grantLabel, onClick = onGrant, enabled = wait == 0)
      }
    }
  }
}
