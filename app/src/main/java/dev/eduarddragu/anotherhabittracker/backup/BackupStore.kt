package dev.eduarddragu.anotherhabittracker.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.domain.BackupFile
import dev.eduarddragu.anotherhabittracker.domain.Backups
import dev.eduarddragu.anotherhabittracker.reminders.Notifications
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Where the nightly copy goes and how the last one went. */
data class NightlyState(val uri: Uri?, val fileName: String?, val lastSaved: Instant?, val lastError: String?)

/**
 * Backups on top of the Storage Access Framework: any provider the phone has (Google Drive, local
 * storage) works, and the app needs no storage permission.
 *
 * The "nightly" file is chosen once and kept through a persisted grant. It's rewritten a couple of
 * minutes after every change and again after midnight, by [BackupWorker], so a session logged late
 * for yesterday is in it too. Every save also writes a local copy to the app's external files folder,
 * which `adb pull` can read on a release build (the weekly review uses it).
 */
class BackupStore(private val app: HabitApp) {
  private val prefs = app.getSharedPreferences("backup", Context.MODE_PRIVATE)
  private val _nightly = MutableStateFlow(readState())

  val nightly: StateFlow<NightlyState> = _nightly.asStateFlow()

  private val folder: File?
    get() = app.getExternalFilesDir(null)

  /** Asks for a save soon; repeated asks within the delay collapse into one. */
  fun scheduleSave(delayMinutes: Long = SAVE_DELAY_MINUTES) {
    val request = OneTimeWorkRequestBuilder<BackupWorker>().setInitialDelay(delayMinutes, TimeUnit.MINUTES).build()
    WorkManager.getInstance(app).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
  }

  suspend fun exportTo(uri: Uri) =
    withContext(Dispatchers.IO) {
      val text = encodeAll()
      write(uri, text)
      verify(uri, text)
    }

  /** Reads and checks a file; nothing changes until [restore]. Throws BackupException with a readable message. */
  suspend fun read(uri: Uri): BackupFile =
    withContext(Dispatchers.IO) {
      val text = app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Couldn't open the file.")
      Backups.decode(text)
    }

  /**
   * Replaces everything with [file], then puts reminders, notifications and the backups in line with
   * it. What was here before is saved first to the app's folder (before-import-<time>.json), in case
   * the wrong file was picked. Runs in the app's scope: leaving the screen mid-way can't stop it
   * between the replace and the rescheduling.
   */
  suspend fun restore(file: BackupFile) =
    withContext(app.appScope.coroutineContext) {
      folder?.let { atomicWrite(File(it, "before-import-${System.currentTimeMillis()}.json"), encodeAll()) }
      val before = app.repository.restore(file)
      before.forEach { Notifications.dismiss(app, it.id) }
      val kept = file.habits.map { it.id }.toSet()
      before.filter { it.id !in kept }.forEach { app.scheduler.cancel(it.id) }
      app.scheduler.scheduleAll(app.repository.habits())
      scheduleSave(delayMinutes = 0)
    }

  /** Uses [uri] (just created by the user) for the nightly copy from now on, and saves to it right away. */
  suspend fun chooseNightly(uri: Uri) =
    withContext(Dispatchers.IO) {
      app.contentResolver.takePersistableUriPermission(uri, GRANT)
      nightlyUri()?.takeIf { it != uri }?.let { old -> runCatching { app.contentResolver.releasePersistableUriPermission(old, GRANT) } }
      prefs.edit { putString(KEY_URI, uri.toString()).putString(KEY_NAME, displayName(uri)).remove(KEY_SAVED).remove(KEY_ERROR) }
      save()
    }

  fun stopNightly() {
    nightlyUri()?.let { runCatching { app.contentResolver.releasePersistableUriPermission(it, GRANT) } }
    prefs.edit { remove(KEY_URI).remove(KEY_NAME).remove(KEY_SAVED).remove(KEY_ERROR) }
    _nightly.value = readState()
  }

  /**
   * Writes the local copy, and the chosen file if there is one, then reads the chosen file back: some
   * providers ignore truncation, which would leave the tail of a longer old file behind. Never throws;
   * the outcome is recorded and shown on Home and on the Backup screen.
   */
  suspend fun save() =
    withContext(Dispatchers.IO) {
      val text = encodeAll()
      folder?.let { dir -> runCatching { atomicWrite(File(dir, Backups.NIGHTLY_NAME), text) }.onFailure { Log.e(HabitApp.TAG, "Local backup failed", it) } }
      val uri = nightlyUri()
      if (uri != null) {
        val result = runCatching {
          if (!hasGrant(uri)) throw SecurityException("grant lost")
          write(uri, text)
          verify(uri, text)
        }
        prefs.edit {
          result
            .onSuccess { putLong(KEY_SAVED, System.currentTimeMillis()).remove(KEY_ERROR) }
            .onFailure { error ->
              Log.e(HabitApp.TAG, "Nightly backup failed", error)
              putString(
                KEY_ERROR,
                when (error) {
                  is SecurityException -> "The app lost access to the backup file. Choose it again."
                  is java.io.FileNotFoundException -> "The backup file is gone. Choose a new one."
                  else -> "Couldn't write the backup file (${error.javaClass.simpleName})."
                },
              )
            }
        }
      }
      _nightly.value = readState()
    }

  private suspend fun encodeAll(): String = Backups.encode(app.repository.backup(appVersionCode()))

  private fun appVersionCode(): Long = app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode

  /** Truncates and rewrites. Some providers don't know the "wt" mode, so plain "w" is the fallback. */
  private fun write(uri: Uri, text: String) {
    val bytes = text.encodeToByteArray()
    val stream = runCatching { app.contentResolver.openOutputStream(uri, "wt") }.getOrNull() ?: app.contentResolver.openOutputStream(uri, "w") ?: error("Couldn't open the file.")
    stream.use { it.write(bytes) }
  }

  private fun verify(uri: Uri, text: String) {
    val back = app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    if (back != text) throw java.io.IOException("the file didn't read back as written")
  }

  /** Written to a temporary file and renamed: a crash halfway never leaves half a file behind. */
  private fun atomicWrite(file: File, text: String) {
    val atomic = AtomicFile(file)
    val stream = atomic.startWrite()
    try {
      stream.write(text.encodeToByteArray())
      atomic.finishWrite(stream)
    } catch (error: Exception) {
      atomic.failWrite(stream)
      throw error
    }
  }

  private fun hasGrant(uri: Uri): Boolean = app.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

  private fun nightlyUri(): Uri? = prefs.getString(KEY_URI, null)?.toUri()

  // No provider queries here: this runs on the main thread when the store is created.
  private fun readState(): NightlyState {
    val uri = nightlyUri()
    return NightlyState(
      uri = uri,
      fileName = prefs.getString(KEY_NAME, null),
      lastSaved = prefs.getLong(KEY_SAVED, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
      lastError = prefs.getString(KEY_ERROR, null),
    )
  }

  private fun displayName(uri: Uri): String? =
    runCatching {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
      }
      .getOrNull()

  private companion object {
    const val KEY_URI = "nightly_uri"
    const val KEY_NAME = "nightly_name"
    const val KEY_SAVED = "nightly_saved_at"
    const val KEY_ERROR = "nightly_error"
    const val WORK_NAME = "backup"
    const val SAVE_DELAY_MINUTES = 2L
    const val GRANT = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
  }
}

/** Runs [BackupStore.save] outside any receiver's time limit; WorkManager also survives the process dying. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    (applicationContext as HabitApp).backups.save()
    return Result.success()
  }
}
