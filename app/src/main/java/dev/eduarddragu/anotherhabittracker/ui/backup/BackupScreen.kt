package dev.eduarddragu.anotherhabittracker.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.domain.BackupException
import dev.eduarddragu.anotherhabittracker.domain.BackupFile
import dev.eduarddragu.anotherhabittracker.domain.Backups
import dev.eduarddragu.anotherhabittracker.ui.components.ScreenTitle
import dev.eduarddragu.anotherhabittracker.ui.components.SectionLabel
import dev.eduarddragu.anotherhabittracker.ui.components.TextAction
import dev.eduarddragu.anotherhabittracker.ui.components.cardOutline
import dev.eduarddragu.anotherhabittracker.ui.components.screenPadding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BackupViewModel(private val app: HabitApp) : ViewModel() {
  val nightly = app.backups.nightly

  private val _busy = MutableStateFlow(false)
  val busy: StateFlow<Boolean> = _busy.asStateFlow()

  /** The outcome of the last action, in one plain sentence, and whether it went wrong. */
  private val _message = MutableStateFlow<Pair<String, Boolean>?>(null)
  val message: StateFlow<Pair<String, Boolean>?> = _message.asStateFlow()

  /** A file read and checked, waiting for the user to confirm the replace. */
  private val _pending = MutableStateFlow<BackupFile?>(null)
  val pending: StateFlow<BackupFile?> = _pending.asStateFlow()

  fun today() = app.repository.today()

  // The first save never throws (its outcome is recorded for Home), so a failure is read back here:
  // "set up and saved" must not show under an error.
  fun chooseNightly(uri: Uri) =
    run("Nightly backup set up and saved.") {
      app.backups.chooseNightly(uri)
      app.backups.nightly.value.lastError?.let { throw BackupException(it) }
    }

  fun saveNow() = run(null) { app.backups.save() }

  fun stopNightly() = run("Nightly backup stopped. The file stays where it is.") { app.backups.stopNightly() }

  fun exportTo(uri: Uri) = run("Exported.") { app.backups.exportTo(uri) }

  fun read(uri: Uri) = run(null) { _pending.value = app.backups.read(uri) }

  fun confirmImport() {
    val file = _pending.value ?: return
    _pending.value = null
    run("Imported ${Backups.summary(file)}.", failure = "The import stopped halfway. The data from before it is in the app's folder.") { app.backups.restore(file) }
  }

  fun cancelImport() {
    _pending.value = null
  }

  private fun run(success: String?, failure: String = "Something went wrong with the file. Nothing was changed.", block: suspend () -> Unit) {
    if (_busy.value) return
    _busy.value = true
    viewModelScope.launch {
      _message.value =
        try {
          block()
          success?.let { it to false }
        } catch (error: BackupException) {
          error.message?.let { it to true }
        } catch (error: Exception) {
          failure to true
        } finally {
          _busy.value = false
        }
    }
  }
}

private val SAVED_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)

/** "Mon 28 Sep, 00:01" in the phone's zone. */
fun formatSaved(instant: Instant): String = instant.atZone(ZoneId.systemDefault()).format(SAVED_FORMAT)

/**
 * Backup and restore: one JSON file with everything. Every night the app rewrites a file the user
 * picked once (Google Drive works); by hand, a copy can be exported or a file imported, which
 * replaces everything after a confirmation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupScreen(app: HabitApp, modifier: Modifier = Modifier, viewModel: BackupViewModel = viewModel { BackupViewModel(app) }) {
  val nightly by viewModel.nightly.collectAsStateWithLifecycle()
  val busy by viewModel.busy.collectAsStateWithLifecycle()
  val message by viewModel.message.collectAsStateWithLifecycle()
  val pending by viewModel.pending.collectAsStateWithLifecycle()
  val colors = MaterialTheme.colorScheme

  val chooseNightly = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::chooseNightly) }
  val exportCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::exportTo) }
  val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::read) }

  Column(modifier.verticalScroll(rememberScrollState()).padding(screenPadding()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      ScreenTitle("Backup", "Your data")
      Text("Everything the app knows, as one JSON file: habits, sessions, known topics.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SectionLabel("Every night")
      val uri = nightly.uri
      if (uri == null) {
        Text("Pick a file once, on Google Drive or anywhere else, and the app rewrites it every night.", style = MaterialTheme.typography.bodyLarge)
        Button(shape = MaterialTheme.shapes.medium, onClick = { chooseNightly.launch(Backups.NIGHTLY_NAME) }, enabled = !busy) { Text("Choose a file") }
      } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(nightly.fileName ?: "Chosen file", style = MaterialTheme.typography.titleMedium)
            val error = nightly.lastError
            val saved = nightly.lastSaved
            Text(
              error ?: saved?.let { "Saved ${formatSaved(it)}" } ?: "Not saved yet",
              style = MaterialTheme.typography.bodyMedium,
              color = if (error != null) colors.primary else colors.onSurfaceVariant,
            )
          }
          TextAction("Change", onClick = { chooseNightly.launch(Backups.NIGHTLY_NAME) }, enabled = !busy)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(shape = MaterialTheme.shapes.medium, onClick = viewModel::saveNow, enabled = !busy) { Text("Save now") }
          OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = viewModel::stopNightly, enabled = !busy, border = cardOutline()) { Text("Stop") }
        }
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SectionLabel("By hand")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { exportCopy.launch(Backups.fileName(viewModel.today())) }, enabled = !busy, border = cardOutline()) { Text("Export a copy") }
        OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy, border = cardOutline()) { Text("Import") }
      }
      Text(
        "Importing replaces everything on this phone. What's here now is saved to the app's own folder first, just in case.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
      )
    }

    // Good news in the quiet color; only a problem is in the accent.
    message?.let { (text, isError) -> Text(text, style = MaterialTheme.typography.bodyMedium, color = if (isError) colors.primary else colors.onSurfaceVariant) }
  }

  pending?.let { file ->
    val from = runCatching { formatSaved(Instant.parse(file.exportedAt)) }.getOrDefault("an unknown date")
    AlertDialog(
      onDismissRequest = viewModel::cancelImport,
      title = { Text("Replace everything?") },
      text = { Text("What's on this phone will be replaced with the backup from $from: ${Backups.summary(file)}.") },
      confirmButton = { TextButton(onClick = viewModel::confirmImport) { Text("Replace") } },
      dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Cancel") } },
    )
  }
}
