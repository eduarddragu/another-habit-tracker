package dev.eduarddragu.anotherhabittracker.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.TextStyle
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.MainActivity
import dev.eduarddragu.anotherhabittracker.R
import dev.eduarddragu.anotherhabittracker.data.HabitStatus
import dev.eduarddragu.anotherhabittracker.data.reminderTimesOn
import dev.eduarddragu.anotherhabittracker.domain.Cell
import dev.eduarddragu.anotherhabittracker.domain.Chores
import dev.eduarddragu.anotherhabittracker.domain.Descriptions
import dev.eduarddragu.anotherhabittracker.domain.HabitKind
import dev.eduarddragu.anotherhabittracker.domain.Heatmap
import dev.eduarddragu.anotherhabittracker.domain.PhoneClock
import dev.eduarddragu.anotherhabittracker.domain.Practices
import dev.eduarddragu.anotherhabittracker.reminders.Notifications
import dev.eduarddragu.anotherhabittracker.theme.Dark
import dev.eduarddragu.anotherhabittracker.theme.Light
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The app palette as day/night pairs. Glance hands both to the launcher, which picks one by its own
 * theme: on a launcher set to dark, the widget is dark even when the phone is light.
 */
private object WidgetColors {
  val background = ColorProvider(day = Light.background, night = Dark.background)
  val text = ColorProvider(day = Light.text, night = Dark.text)
  val muted = ColorProvider(day = Light.muted, night = Dark.muted)
  // One step darker at night: the plain border color nearly disappears on the dark background.
  val empty = ColorProvider(day = Light.border, night = Dark.borderStrong)
  val accent = ColorProvider(day = Light.accent, night = Dark.accent)
  val warm = ColorProvider(day = Light.accentContainer, night = Dark.accentContainer)
  // The site's fg-faint, one step below muted: the detail line under a habit's name (design-system
  // item 5; theme/Color.kt has no faint level yet, move it there when it gets one).
  val faint = ColorProvider(day = Color(0xFF766859), night = Color(0xFF9A8C7E))
}

private val HEADER_DATE = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)

private val label = TextStyle(color = WidgetColors.muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

private fun homeIntent(context: Context): Intent =
  Intent(context, MainActivity::class.java).putExtra(Notifications.EXTRA_OPEN_HOME, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

/** Study habits open on today's topic; the others straight on the log form. */
private fun openIntent(context: Context, status: HabitStatus): Intent {
  val finished = status.doneToday || status.frozenToday
  val extra = if (status.habit.kind == HabitKind.STUDY || finished) Notifications.EXTRA_OPEN_HABIT_ID else Notifications.EXTRA_LOG_HABIT_ID
  return Intent(context, MainActivity::class.java)
    .putExtra(extra, status.habit.id)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}

/** Today at a glance: what's done, what's left, the study topic, this week and the streaks. */
class TodayWidget : GlanceAppWidget() {
  // Exact: the layout follows the size the launcher actually gives the widget.
  override val sizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val repository = (context.applicationContext as HabitApp).repository
    val initial = repository.statuses()
    provideContent {
      // A session lives ~45 s and update() only recomposes it, so observe the data instead of
      // relying on the snapshot loaded above: a log made meanwhile shows up at once.
      val statuses by remember { repository.observeStatuses() }.collectAsState(initial)
      TodayContent(statuses)
    }
  }

  override suspend fun providePreview(context: Context, widgetCategory: Int) {
    val statuses = (context.applicationContext as HabitApp).repository.statuses()
    provideContent { TodayContent(statuses) }
  }
}

/**
 * How the habits fit the widget. Glance text is in sp and the box in dp, so every height is estimated
 * at the font scale in use. [roomy] rows (name, what's next, week) need about 50dp each; otherwise each
 * habit gets one line, and on a short widget (Niagara gives it about 70dp) the header goes first, then
 * some padding, then the lines split into two columns, and only then are habits left out.
 */
private data class Fit(val roomy: Boolean, val header: Boolean, val columns: Int, val shown: Int, val padding: Float)

private fun fit(count: Int, width: Float, height: Float, fontScale: Float): Fit {
  if (height >= 24f + 20f * fontScale + count * (22f + 28f * fontScale)) return Fit(roomy = true, header = true, columns = 1, shown = count, padding = 12f)
  val row = 4f + 19f * fontScale
  val header = 2f + 15f * fontScale
  // A compact line needs room for a name, the seven marks and the streak.
  val twoColumns = count > 1 && width - 20f >= 2 * (150f + 20f * fontScale) + 8f
  val tries =
    listOf(Fit(false, true, 1, count, 8f), Fit(false, false, 1, count, 8f), Fit(false, false, 1, count, 4f)) +
      if (twoColumns) listOf(Fit(false, true, 2, count, 8f), Fit(false, false, 2, count, 8f), Fit(false, false, 2, count, 4f)) else emptyList()
  tries.firstOrNull { 2 * it.padding + (if (it.header) header else 0f) + (count + it.columns - 1) / it.columns * row <= height }?.let { return it }
  val columns = if (twoColumns) 2 else 1
  val lines = maxOf(1, ((height - 8f) / row).toInt())
  return Fit(false, false, columns, minOf(count, lines * columns), 4f)
}

/** What TalkBack reads for a habit's row: its name, today, this week and the streak in one sentence. */
private fun describe(status: HabitStatus): String {
  val today =
    when {
      status.frozenToday -> "frozen today"
      status.doneToday -> "done today"
      status.summary.pausedToday -> "day off"
      else -> "not done yet"
    }
  return listOfNotNull(status.habit.name, today, Descriptions.week(status.cells, status.summary.daysOff, status.today), Descriptions.streak(status.streak).takeIf { status.streak > 0 })
    .joinToString(", ")
}

@Composable
private fun TodayContent(statuses: List<HabitStatus>) {
  val context = LocalContext.current
  // A habit on a day off has nothing to do today: it counts neither as done nor as left.
  val counted = statuses.filterNot { it.summary.pausedToday }
  val done = counted.count { it.doneToday || it.frozenToday }
  val tally = if (counted.isEmpty() && statuses.isNotEmpty()) "DAY OFF" else "$done OF ${counted.size}"
  val today = statuses.firstOrNull()?.today
  val size = LocalSize.current
  val fontScale = context.resources.configuration.fontScale
  val fit = fit(statuses.size, size.width.value, size.height.value, fontScale)
  // Left out only when nothing else made them fit: the open ones stay, in their usual order.
  val shown =
    if (fit.shown >= statuses.size) statuses
    else statuses.withIndex().sortedBy { (_, it) -> it.doneToday || it.frozenToday || it.summary.pausedToday }.take(fit.shown).sortedBy { it.index }.map { it.value }
  // One width for every compact streak, so the marks line up and three digits still fit at a large font.
  val digits = shown.maxOfOrNull { it.streak.toString().length } ?: 1
  val streakWidth = (maxOf(2, digits) * 9 + 8) * fontScale
  Column(
    GlanceModifier.fillMaxSize()
      .appWidgetBackground()
      .background(WidgetColors.background)
      .cornerRadius(android.R.dimen.system_app_widget_background_radius)
      // A tap anywhere but on a habit opens Home; the rows have their own targets.
      .clickable(actionStartActivity(homeIntent(context)))
      .padding(horizontal = 10.dp, vertical = fit.padding.dp),
    verticalAlignment = if (fit.roomy) Alignment.Vertical.Top else Alignment.Vertical.CenterVertically,
  ) {
    if (fit.header) {
      // Like the app's labels: the date muted, only the state in the accent.
      Row(GlanceModifier.padding(start = 6.dp)) {
        val date = today?.format(HEADER_DATE)?.uppercase()
        if (date != null) Text("$date · ", style = label)
        // A day off is no state to call out: the whole line stays muted.
        Text(tally, style = if (counted.isEmpty()) label else label.copy(color = WidgetColors.accent))
      }
      Spacer(GlanceModifier.height(if (fit.roomy) 6.dp else 2.dp))
    }
    if (statuses.isEmpty()) Text("No habits yet.", style = TextStyle(color = WidgetColors.muted, fontSize = 13.sp), modifier = GlanceModifier.padding(start = 6.dp))
    if (fit.roomy) {
      shown.forEach { status ->
        TodayRow(context, status)
        Spacer(GlanceModifier.height(4.dp))
      }
    } else if (fit.columns == 1) {
      shown.forEach { CompactRow(context, it, streakWidth) }
    } else {
      // Two columns, read down: the first half on the left.
      val half = (shown.size + 1) / 2
      Row(GlanceModifier.fillMaxWidth()) {
        Column(GlanceModifier.defaultWeight()) { shown.take(half).forEach { CompactRow(context, it, streakWidth) } }
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) { shown.drop(half).forEach { CompactRow(context, it, streakWidth) } }
      }
    }
  }
}

/** One habit: name, what's next, this week as seven marks, and the streak. Warm once done. */
@Composable
private fun TodayRow(context: Context, status: HabitStatus) {
  val finished = status.doneToday || status.frozenToday
  val base = GlanceModifier.fillMaxWidth().cornerRadius(12.dp).padding(horizontal = 6.dp, vertical = 5.dp)
  Row(
    (if (finished) base.background(WidgetColors.warm) else base).clickable(actionStartActivity(openIntent(context, status))).semantics { contentDescription = describe(status) },
    verticalAlignment = Alignment.Vertical.CenterVertically,
  ) {
    Column(GlanceModifier.defaultWeight()) {
      Text(
        status.habit.name,
        style = TextStyle(color = WidgetColors.text, fontSize = 15.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
        maxLines = 1,
      )
      val pick = status.pick
      val book = status.books?.current?.title
      val detail =
        when {
          status.frozenToday -> "Frozen"
          status.doneToday -> "Done"
          status.summary.pausedToday -> "Day off"
          pick != null -> pick.topic.title
          book != null -> book
          Practices.appliesTo(status.habit.name, status.habit.linkedPackage) -> Practices.forDay(status.today).title
          Chores.appliesTo(status.habit.kind, status.habit.name, status.habit.icon) -> Chores.day(status.today).motto
          else -> null
        }
      // Still open after the last call: the one hot state, in the accent like Home's card line.
      val urgent = lastCallPassed(status)
      if (detail != null) Text(detail, style = TextStyle(color = if (urgent) WidgetColors.accent else WidgetColors.faint, fontSize = 11.sp), maxLines = 1)
      Spacer(GlanceModifier.height(3.dp))
      WeekMarks(status)
    }
    if (status.streak > 0) {
      Spacer(GlanceModifier.width(6.dp))
      Text(status.streak.toString(), style = TextStyle(color = WidgetColors.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold))
    }
  }
}

/**
 * The day is still open and its last reminder has passed, as Home's cards judge it. Read at
 * composition: the reminder that fires at that minute refreshes the widget.
 */
private fun lastCallPassed(status: HabitStatus): Boolean =
  status.summary.dayOpen && status.habit.reminderTimesOn(status.today).lastOrNull()?.let { !LocalTime.now(PhoneClock).isBefore(it) } == true

/** One line per habit for short widgets: name, this week, streak. Warm once done. */
@Composable
private fun CompactRow(context: Context, status: HabitStatus, streakWidth: Float) {
  val finished = status.doneToday || status.frozenToday
  val base = GlanceModifier.fillMaxWidth().cornerRadius(10.dp).padding(horizontal = 6.dp, vertical = 2.dp)
  Row(
    (if (finished) base.background(WidgetColors.warm) else base).clickable(actionStartActivity(openIntent(context, status))).semantics { contentDescription = describe(status) },
    verticalAlignment = Alignment.Vertical.CenterVertically,
  ) {
    Text(
      status.habit.name,
      style = TextStyle(color = WidgetColors.text, fontSize = 14.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
      maxLines = 1,
      modifier = GlanceModifier.defaultWeight(),
    )
    WeekMarks(status)
    Text(
      if (status.streak > 0) status.streak.toString() else "",
      style = TextStyle(color = WidgetColors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
      maxLines = 1,
      modifier = GlanceModifier.width(streakWidth.dp),
    )
  }
}

/** The app's week strip, Monday to Sunday, in seven tinted marks. */
@Composable
private fun WeekMarks(status: HabitStatus) {
  Row {
    Heatmap.weeks(status.today, 1).first().forEachIndexed { index, day ->
      if (index > 0) Spacer(GlanceModifier.width(2.dp))
      val cell = day?.let { status.cells[it] } ?: Cell.Empty
      val (mark, color) =
        when {
          day == null -> R.drawable.widget_mark_dot to WidgetColors.empty
          cell is Cell.Done -> R.drawable.widget_mark_solid to WidgetColors.accent
          cell is Cell.Frozen -> R.drawable.widget_mark_frozen to WidgetColors.muted
          day == status.today -> R.drawable.widget_mark_open to WidgetColors.accent
          else -> R.drawable.widget_mark_solid to WidgetColors.empty
        }
      Image(provider = ImageProvider(mark), contentDescription = null, colorFilter = ColorFilter.tint(color), modifier = GlanceModifier.size(8.dp))
    }
  }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

object HabitWidgets {
  suspend fun refresh(context: Context) = TodayWidget().updateAll(context)

  /** Picker preview rendered from real data (Android 15+). The system rate-limits it, so call it rarely. */
  suspend fun publishPreviews(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    runCatching { GlanceAppWidgetManager(context).setWidgetPreviews(TodayWidgetReceiver::class) }
  }
}
