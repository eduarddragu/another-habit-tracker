package dev.eduarddragu.anotherhabittracker.ui.components

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eduarddragu.anotherhabittracker.HabitApp
import dev.eduarddragu.anotherhabittracker.data.HabitStatus
import dev.eduarddragu.anotherhabittracker.domain.Curriculum
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Every action a screen starts goes through here: a failed write must not take the app down, so it's
 * logged and the screen simply stays where it is. Cancellation (the screen going away) passes through.
 */
fun ViewModel.launchSafely(block: suspend () -> Unit): Job =
  viewModelScope.launch {
    try {
      block()
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      Log.e(HabitApp.TAG, "Action failed", error)
    }
  }

/** Base for screens about one habit: its live status, and actions that can't run twice at once. */
abstract class HabitViewModel(protected val app: HabitApp, protected val habitId: Long) : ViewModel() {
  // Seeded from the cache so the first frame of the push transition already has content.
  val status: StateFlow<HabitStatus?> =
    app.repository.observeStatus(habitId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), app.repository.cachedStatus(habitId))

  val curriculum: Curriculum
    get() = app.repository.curriculum

  private val running = AtomicBoolean(false)
  private val _busy = MutableStateFlow(false)

  /** True while an action started with [once] is running; buttons disable themselves meanwhile. */
  val busy: StateFlow<Boolean> = _busy.asStateFlow()

  /** Runs [block] unless another action is still running, so a double tap can't log or save twice. */
  protected fun once(block: suspend () -> Unit) {
    if (!running.compareAndSet(false, true)) return
    _busy.value = true
    launchSafely {
      try {
        block()
      } finally {
        running.set(false)
        _busy.value = false
      }
    }
  }
}
