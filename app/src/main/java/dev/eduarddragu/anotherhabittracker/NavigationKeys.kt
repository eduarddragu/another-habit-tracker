package dev.eduarddragu.anotherhabittracker

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Home : NavKey

@Serializable data object Backup : NavKey

@Serializable data object Guard : NavKey

@Serializable data class HabitDetail(val habitId: Long) : NavKey

/**
 * The log form; with [entryId], correcting a session already logged; with [onDay] (an epoch day), a
 * new session for that day, e.g. one that was frozen or missed.
 */
@Serializable data class LogEntry(val habitId: Long, val entryId: Long? = null, val onDay: Long? = null, val minutes: Int? = null) : NavKey

@Serializable data class HabitSettings(val habitId: Long) : NavKey

@Serializable data class CurriculumBrowser(val habitId: Long) : NavKey
