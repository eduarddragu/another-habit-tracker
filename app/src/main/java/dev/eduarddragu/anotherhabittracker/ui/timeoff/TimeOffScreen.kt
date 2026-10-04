package dev.eduarddragu.anotherhabittracker.ui.timeoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.domain.TimeOff
import dev.eduarddragu.anotherhabittracker.domain.TimeOffPeriod
import dev.eduarddragu.anotherhabittracker.reminders.Notifications
import dev.eduarddragu.anotherhabittracker.ui.components.ScreenTitle
import dev.eduarddragu.anotherhabittracker.ui.components.SectionLabel
import dev.eduarddragu.anotherhabittracker.ui.components.TextAction
import dev.eduarddragu.anotherhabittracker.ui.components.launchSafely
import dev.eduarddragu.anotherhabittracker.ui.components.rememberArrival
import dev.eduarddragu.anotherhabittracker.ui.components.rise
import dev.eduarddragu.anotherhabittracker.ui.components.screenPadding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class TimeOffViewModel(private val app: HabitApp) : ViewModel() {
  val periods = app.repository.observeTimeOff().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  fun today(): LocalDate = app.repository.today()

  /** Starts it, and clears any reminder already up: nothing nags from here. */
  fun start(from: LocalDate, until: LocalDate?) =
    launchSafely {
      app.repository.startTimeOff(from, until)
      Notifications.dismissAll(app)
    }

  /** Back today: the last day off was yesterday (a period that started today simply goes away). */
  fun end(period: TimeOffPeriod) = launchSafely { app.repository.endTimeOff(period.id, today().minusDays(1)) }

  fun remove(period: TimeOffPeriod) = launchSafely { app.repository.deleteTimeOff(period.id) }
}

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** "Fri 2 Oct" */
private fun LocalDate.label(): String = format(DAY)

/**
 * Time off: days that neither count nor break a streak, with nothing nagging on them. It can start in
 * the past, to cover days already missed, and stay open until he's back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOffScreen(app: HabitApp, modifier: Modifier = Modifier, viewModel: TimeOffViewModel = viewModel { TimeOffViewModel(app) }) {
  val periods by viewModel.periods.collectAsStateWithLifecycle()
  val all = periods ?: return
  val today = viewModel.today()
  val current = TimeOff.current(all, today)
  val colors = MaterialTheme.colorScheme
  val arrival = rememberArrival(3)

  var from by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
  var until by rememberSaveable { mutableStateOf<Long?>(null) }
  var picking by rememberSaveable { mutableStateOf<String?>(null) }

  Column(modifier.verticalScroll(rememberScrollState()).padding(screenPadding()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    Column(Modifier.rise(arrival[0], 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      ScreenTitle("Time off", "Take the time")
      Text(
        "Days off don't count and don't break a streak. No reminders, no guard, no recap while they last. Anything you do manage to log still counts.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
      )
    }

    Column(Modifier.rise(arrival[1], 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (current != null) {
        SectionLabel("Now")
        val range = if (current.end == null) "Since ${current.start.label()}, until you're back." else "${current.start.label()} to ${current.end.label()}."
        Text(range, style = MaterialTheme.typography.bodyLarge)
        Text("The streak waits. Come back when you can.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Button(onClick = { viewModel.end(current) }, shape = MaterialTheme.shapes.medium) { Text("I'm back") }
      } else {
        SectionLabel("Start")
        DateRow("From", LocalDate.ofEpochDay(from).let { if (it == today) "Today" else it.label() }, onChange = { picking = "from" })
        DateRow("Until", until?.let { LocalDate.ofEpochDay(it).label() } ?: "I'm back", onChange = { picking = "until" })
        if (until != null) TextAction("Until I'm back instead", onClick = { until = null })
        Button(
          onClick = { viewModel.start(LocalDate.ofEpochDay(from), until?.let(LocalDate::ofEpochDay)) },
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.padding(top = 8.dp),
        ) {
          Text("Start time off")
        }
        Text(
          "It can start up to ${TimeOff.MAX_DAYS_BACK} days back, to cover days already gone.",
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
      }
    }

    val past = all.filter { it != current }.sortedByDescending { it.start }
    if (past.isNotEmpty()) {
      Column(Modifier.rise(arrival[2], 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Earlier")
        past.forEach { period ->
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
              if (period.end == null || period.end == period.start) period.start.label() else "${period.start.label()} to ${period.end.label()}",
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.weight(1f),
            )
            TextAction("Remove", onClick = { viewModel.remove(period) })
          }
        }
      }
    }
  }

  picking?.let { which ->
    val min = today.minusDays(TimeOff.MAX_DAYS_BACK)
    val initial = if (which == "from") LocalDate.ofEpochDay(from) else until?.let(LocalDate::ofEpochDay) ?: today.plusDays(7)
    val earliest = if (which == "from") min else LocalDate.ofEpochDay(from)
    val latest = if (which == "from") today else today.plusYears(1)
    val state =
      rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates =
          object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
              val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
              return !day.isBefore(earliest) && !day.isAfter(latest)
            }
          },
      )
    DatePickerDialog(
      onDismissRequest = { picking = null },
      confirmButton = {
        TextButton(
          onClick = {
            state.selectedDateMillis?.let { millis ->
              val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
              if (which == "from") {
                from = day
                if (until != null && until!! < day) until = null
              } else until = day
            }
            picking = null
          }
        ) {
          Text("Set")
        }
      },
      dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
    ) {
      DatePicker(state)
    }
  }
}

@Composable
private fun DateRow(label: String, value: String, onChange: () -> Unit) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(value, style = MaterialTheme.typography.bodyLarge)
    }
    TextAction("Change", onClick = onChange)
  }
}
