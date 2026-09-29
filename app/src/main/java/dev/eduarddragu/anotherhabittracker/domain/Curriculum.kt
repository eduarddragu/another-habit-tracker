package dev.eduarddragu.anotherhabittracker.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class Area(val id: String, val name: String, val weight: Double = 1.0)

/** One ~30 minute study session. `requires` lists direct prerequisites only. */
@Serializable
data class Topic(val id: String, val area: String, val title: String, val requires: List<String> = emptyList(), val hints: List<String> = emptyList())

/** The study graph bundled with the app (assets/curriculum.json). */
@Serializable
data class Curriculum(val version: Int, val areas: List<Area>, val topics: List<Topic>) {
  val byId: Map<String, Topic> by lazy { topics.associateBy { it.id } }
  val areaById: Map<String, Area> by lazy { areas.associateBy { it.id } }

  /** Every problem that would make the graph unusable; empty when it's valid. */
  fun problems(): List<String> {
    val problems = mutableListOf<String>()
    topics.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { problems += "duplicate id: $it" }
    for (topic in topics) {
      if (topic.area !in areaById) problems += "${topic.id}: unknown area '${topic.area}'"
      topic.requires.filter { it !in byId }.forEach { problems += "${topic.id}: unknown prerequisite '$it'" }
    }
    // Depth-first search for cycles.
    val state = mutableMapOf<String, Int>() // 1 = visiting, 2 = done
    fun visit(id: String, path: List<String>) {
      when (state[id]) {
        2 -> return
        1 -> {
          problems += "cycle: " + (path.dropWhile { it != id } + id).joinToString(" -> ")
          return
        }
      }
      state[id] = 1
      byId[id]?.requires?.forEach { visit(it, path + id) }
      state[id] = 2
    }
    topics.forEach { visit(it.id, emptyList()) }
    return problems
  }

  /** The given topics plus everything they depend on, transitively. */
  fun withPrerequisites(ids: Collection<String>): Set<String> {
    val out = mutableSetOf<String>()
    val stack = ids.filter { it in byId }.toMutableList()
    while (stack.isNotEmpty()) {
      val id = stack.removeAt(stack.lastIndex)
      if (out.add(id)) stack += byId.getValue(id).requires.filter { it in byId }
    }
    return out
  }

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): Curriculum = json.decodeFromString(serializer(), text)
  }
}
