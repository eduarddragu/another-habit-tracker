package dev.eduarddragu.anotherhabittracker.widget

import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.glance.text.TextStyle
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.MainActivity
import dev.eduarddragu.anotherhabittracker.R
import dev.eduarddragu.anotherhabittracker.data.HabitStatus
import dev.eduarddragu.anotherhabittracker.domain.Cell
import dev.eduarddragu.anotherhabittracker.domain.HabitKind
import dev.eduarddragu.anotherhabittracker.domain.Heatmap
import dev.eduarddragu.anotherhabittracker.domain.Practices
import dev.eduarddragu.anotherhabittracker.reminders.Notifications
import dev.eduarddragu.anotherhabittracker.theme.Dark
import dev.eduarddragu.anotherhabittracker.theme.Light
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

@Composable
private fun TodayContent(statuses: List<HabitStatus>) {
  val context = LocalContext.current
  val done = statuses.count { it.doneToday || it.frozenToday }
  val today = statuses.firstOrNull()?.today
  // Roomy rows (name, topic, week) need about 50dp each; below that every habit gets one line.
  val height = LocalSize.current.height
  val roomy = height >= 44.dp + 50.dp * statuses.size
  Column(
    GlanceModifier.fillMaxSize()
      .appWidgetBackground()
      .background(WidgetColors.background)
      .cornerRadius(android.R.dimen.system_app_widget_background_radius)
      // A tap anywhere but on a habit opens Home; the rows have their own targets.
      .clickable(actionStartActivity(homeIntent(context)))
      .padding(horizontal = 10.dp, vertical = if (roomy) 12.dp else 8.dp),
    verticalAlignment = if (roomy) Alignment.Vertical.Top else Alignment.Vertical.CenterVertically,
  ) {
    val date = today?.format(HEADER_DATE)?.uppercase()
    Text(listOfNotNull(date, "$done OF ${statuses.size}").joinToString(" · "), style = label.copy(color = WidgetColors.accent), modifier = GlanceModifier.padding(start = 6.dp))
    Spacer(GlanceModifier.height(if (roomy) 6.dp else 2.dp))
    if (statuses.isEmpty()) Text("No habits yet", style = label)
    statuses.forEach { status ->
      if (roomy) {
        TodayRow(context, status)
        Spacer(GlanceModifier.height(4.dp))
      } else {
        CompactRow(context, status)
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
    (if (finished) base.background(WidgetColors.warm) else base).clickable(actionStartActivity(openIntent(context, status))),
    verticalAlignment = Alignment.Vertical.CenterVertically,
  ) {
    Column(GlanceModifier.defaultWeight()) {
      Text(
        status.habit.name,
        style = TextStyle(color = WidgetColors.text, fontSize = 15.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
        maxLines = 1,
      )
      val pick = status.pick
      val detail =
        when {
          status.frozenToday -> "Frozen"
          status.doneToday -> "Done"
          pick != null -> pick.topic.title
          Practices.appliesTo(status.habit.name, status.habit.linkedPackage) -> Practices.forDay(status.today).title
          else -> null
        }
      if (detail != null) Text(detail, style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 1)
      Spacer(GlanceModifier.height(3.dp))
      WeekMarks(status)
    }
    if (status.streak > 0) {
      Spacer(GlanceModifier.width(6.dp))
      Text(status.streak.toString(), style = TextStyle(color = WidgetColors.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold))
    }
  }
}

/** One line per habit for short widgets: name, this week, streak. Warm once done. */
@Composable
private fun CompactRow(context: Context, status: HabitStatus) {
  val finished = status.doneToday || status.frozenToday
  val base = GlanceModifier.fillMaxWidth().cornerRadius(10.dp).padding(horizontal = 6.dp, vertical = 2.dp)
  Row(
    (if (finished) base.background(WidgetColors.warm) else base).clickable(actionStartActivity(openIntent(context, status))),
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
      modifier = GlanceModifier.width(26.dp),
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
