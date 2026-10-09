package dev.eduarddragu.anotherhabittracker

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.eduarddragu.anotherhabittracker.theme.Motion
import dev.eduarddragu.anotherhabittracker.ui.backup.BackupScreen
import dev.eduarddragu.anotherhabittracker.ui.curriculum.CurriculumScreen
import dev.eduarddragu.anotherhabittracker.ui.detail.HabitDetailScreen
import dev.eduarddragu.anotherhabittracker.ui.guard.GuardSettingsScreen
import dev.eduarddragu.anotherhabittracker.ui.timeoff.TimeOffScreen
import dev.eduarddragu.anotherhabittracker.ui.home.HomeScreen
import dev.eduarddragu.anotherhabittracker.ui.log.LogScreen
import dev.eduarddragu.anotherhabittracker.ui.settings.HabitSettingsScreen
import kotlinx.coroutines.launch

/**
 * Transitions move screens, they never fade both at once: two half-transparent screens on top of each
 * other is what made the default back gesture look "glittery". Push: the new screen slides in from
 * the right while the old one drifts a fifth to the left (parallax). Pop and predictive back mirror it.
 */
// 520 ms: the home moves slowly (the planet, the cards), and a quicker push felt disconnected from it.
// The new screen's content then arrives in steps while it slides (see rememberArrival).
private val push = { slideInHorizontally(tween(520, easing = Motion.EaseScreen)) { it } togetherWith slideOutHorizontally(tween(520, easing = Motion.EaseScreen)) { -it / 5 } }
private val pop = { slideInHorizontally(tween(340, easing = Motion.EaseScreen)) { -it / 5 } togetherWith slideOutHorizontally(tween(340, easing = Motion.EaseScreen)) { it } }

/**
 * Same geometry as pop but linear: during the gesture the system seeks this transition with the
 * finger, and an eased curve would run ahead of it. After release it finishes off screen.
 */
private val predictivePop = { slideInHorizontally(tween(340, easing = LinearEasing)) { -it / 5 } togetherWith slideOutHorizontally(tween(340, easing = LinearEasing)) { it } }

/**
 * The log form rises like a sheet over the screen it was opened from, and leaves the same way: down
 * and out, quicker than it came, while the screen underneath stays put. Predictive back seeks the same
 * geometry linearly.
 */
private val sheetMetadata =
  NavDisplay.transitionSpec { slideInVertically(tween(320, easing = Motion.EaseEntrance)) { it / 8 } + fadeIn(tween(180, easing = Motion.EaseUi)) togetherWith ExitTransition.KeepUntilTransitionsFinished } +
    NavDisplay.popTransitionSpec { EnterTransition.None togetherWith slideOutVertically(tween(Motion.EXIT, easing = Motion.EaseUi)) { it / 8 } + fadeOut(tween(160, easing = Motion.EaseUi)) } +
    NavDisplay.predictivePopTransitionSpec { EnterTransition.None togetherWith slideOutVertically(tween(340, easing = LinearEasing)) { it / 8 } + fadeOut(tween(340, easing = LinearEasing)) }

@Composable
fun MainNavigation(
  app: HabitApp,
  notificationsEnabled: Boolean,
  deliveryProblems: List<String>,
  onEnableNotifications: () -> Unit,
  pendingLogHabitId: Long?,
  pendingLogMinutes: Int?,
  pendingLogDay: Long?,
  onPendingLogConsumed: () -> Unit,
  pendingOpenHabitId: Long?,
  onPendingOpenConsumed: () -> Unit,
  pendingHome: Boolean,
  onPendingHomeConsumed: () -> Unit,
) {
  val backStack = rememberNavBackStack(Home)
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  // Screens fill the whole window and paint their own background; each one keeps its content clear of
  // the system bars through its scroll padding (see screenPadding). The Scaffold is only here for the
  // snackbar, so it must not add insets of its own.
  val screen = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)

  // A tapped reminder opens its log screen on top of Home.
  LaunchedEffect(pendingLogHabitId) {
    val id = pendingLogHabitId ?: return@LaunchedEffect
    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    // A session's day, when it's not today (a session that ran past midnight).
    val day = pendingLogDay?.takeIf { it != app.repository.today().toEpochDay() }
    backStack.add(LogEntry(id, onDay = day, minutes = pendingLogMinutes))
    onPendingLogConsumed()
  }
  // A tapped study reminder opens the habit page, where today's topic and its questions are.
  LaunchedEffect(pendingOpenHabitId) {
    val id = pendingOpenHabitId ?: return@LaunchedEffect
    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    backStack.add(HabitDetail(id))
    onPendingOpenConsumed()
  }

  // The widget's background goes back to Home from wherever the app was left.
  LaunchedEffect(pendingHome) {
    if (!pendingHome) return@LaunchedEffect
    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    onPendingHomeConsumed()
  }

  // A double tap would push the same screen twice (the page underneath still takes taps during the
  // slide), and after a save the form would come back empty.
  fun show(key: NavKey) {
    if (backStack.lastOrNull() != key) backStack.add(key)
  }

  /**
   * A message with Undo: the undo runs only if the action is tapped before the snackbar goes. It stays
   * the long time (about ten seconds): the only window to take back a discarded session or a freeze.
   */
  fun undoable(message: String, undo: () -> Unit) {
    scope.launch { if (snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) undo() }
  }

  // A same-day log needs no message: the commit on the screen underneath says it. Only what can't be
  // seen there (a log for yesterday, a refused freeze, saved settings) gets a snackbar.
  // Pops [key] only if it's still on top: back pressed while the write was running must not make the
  // result pop the screen underneath too.
  fun finish(key: NavKey, message: String?) {
    if (backStack.lastOrNull() == key && backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    if (message != null) scope.launch { snackbar.showSnackbar(message) }
  }

  Scaffold(contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) { Snackbar(it, shape = MaterialTheme.shapes.medium) } }) { padding ->
    NavDisplay(
      modifier = Modifier.padding(padding),
      backStack = backStack,
      onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
      entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
      transitionSpec = { push() },
      popTransitionSpec = { pop() },
      predictivePopTransitionSpec = { predictivePop() },
      entryProvider =
        entryProvider {
          entry<Home> {
            HomeScreen(
              app = app,
              notificationsEnabled = notificationsEnabled,
              deliveryProblems = deliveryProblems,
              onEnableNotifications = onEnableNotifications,
              onOpen = { show(HabitDetail(it)) },
              onBackup = { show(Backup) },
              onGuard = { show(Guard) },
              onTimeOff = { show(TimeOffKey) },
              onLogSession = { habitId, minutes, day -> show(LogEntry(habitId, onDay = day.takeIf { it != app.repository.today().toEpochDay() }, minutes = minutes)) },
              onUndoable = ::undoable,
              modifier = screen,
            )
          }
          entry<HabitDetail> { key ->
            HabitDetailScreen(
              app = app,
              habitId = key.habitId,
              onLog = { show(LogEntry(key.habitId)) },
              onSettings = { show(HabitSettings(key.habitId)) },
              onCurriculum = { show(CurriculumBrowser(key.habitId)) },
              onEditEntry = { show(LogEntry(key.habitId, it)) },
              onLogOnDay = { show(LogEntry(key.habitId, onDay = it.toEpochDay())) },
              onSessionStarted = { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
              onLogMinutes = { minutes, day -> show(LogEntry(key.habitId, onDay = day.takeIf { it != app.repository.today().toEpochDay() }, minutes = minutes)) },
              onUndoable = ::undoable,
              onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
              modifier = screen,
            )
          }
          entry<Backup> { BackupScreen(app = app, modifier = screen) }
          entry<Guard> { GuardSettingsScreen(app = app, modifier = screen) }
          entry<TimeOffKey> { TimeOffScreen(app = app, onUndoable = ::undoable, modifier = screen) }
          entry<CurriculumBrowser> { key -> CurriculumScreen(app = app, habitId = key.habitId, onUndoable = ::undoable, modifier = screen) }
          entry<LogEntry>(metadata = sheetMetadata) { key -> LogScreen(app = app, habitId = key.habitId, entryId = key.entryId, onDay = key.onDay, prefillMinutes = key.minutes, onDone = { finish(key, it) }, onDoneUndoable = { message, undo -> finish(key, null); undoable(message, undo) }, modifier = screen) }
          entry<HabitSettings> { key -> HabitSettingsScreen(app = app, habitId = key.habitId, onDone = { finish(key, it) }, modifier = screen) }
        },
    )
  }
}
