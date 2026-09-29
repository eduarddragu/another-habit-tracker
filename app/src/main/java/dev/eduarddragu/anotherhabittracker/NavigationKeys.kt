package dev.eduarddragu.anotherhabittracker

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Home : NavKey

@Serializable data object Backup : NavKey

@Serializable data object Guard : NavKey

@Serializable data class HabitDetail(val habitId: Long) : NavKey

/** The log form; with [entryId], correcting a session already logged. */
@Serializable data class LogEntry(val habitId: Long, val entryId: Long? = null) : NavKey

@Serializable data class HabitSettings(val habitId: Long) : NavKey

@Serializable data class CurriculumBrowser(val habitId: Long) : NavKey
