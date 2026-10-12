package dev.eduarddragu.anotherhabittracker.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class Area(val id: String, val name: String, val weight: Double = 1.0)

/** One ~30 minute study session. `requires` lists direct prerequisites only. */
@Serializable
data class Topic(val id: String, val area: String, val title: String, val requires: List<String> = emptyList(), val hints: List<String> = emptyList())

/** The topic ids in a session's extra topics (comma separated). */
fun extraTopicIds(raw: String): List<String> = raw.split(',').map(String::trim).filter(String::isNotEmpty)

/**
 * An exam studied for over weeks (see Certs): while it's on, it is the study habit's topic every day,
 * and passing it marks [grants] as known. [hints] are how to run a session on it, not exam questions.
 */
@Serializable
data class Cert(val id: String, val code: String, val name: String, val area: String, val grants: List<String>, val hints: List<String> = emptyList()) {
  /** The cert as the screens, sessions and history see a topic: outside the graph, never picked. */
  val topic: Topic
    get() = Topic(id, area, name, hints = hints)
}

/** The study graph bundled with the app (assets/curriculum.json). */
@Serializable
data class Curriculum(val version: Int, val areas: List<Area>, val topics: List<Topic>, val certs: List<Cert> = emptyList()) {
  /** Topics, and certs as topics, so a session logged on a cert reads its title everywhere. */
  val byId: Map<String, Topic> by lazy { (topics + certs.map { it.topic }).associateBy { it.id } }

  /** The graph's own topics, without certs: what can be a prerequisite or be picked. */
  val topicById: Map<String, Topic> by lazy { topics.associateBy { it.id } }
  val areaById: Map<String, Area> by lazy { areas.associateBy { it.id } }

  /** Every problem that would make the graph unusable; empty when it's valid. */
  fun problems(): List<String> {
    val problems = mutableListOf<String>()
    topics.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { problems += "duplicate id: $it" }
    for (topic in topics) {
      if (topic.area !in areaById) problems += "${topic.id}: unknown area '${topic.area}'"
      topic.requires.filter { it !in topicById }.forEach { problems += "${topic.id}: unknown prerequisite '$it'" }
    }
    for (cert in certs) {
      if (cert.id in topicById) problems += "${cert.id}: a cert can't share a topic's id"
      if (cert.area !in areaById) problems += "${cert.id}: unknown area '${cert.area}'"
      cert.grants.filter { it !in topicById }.forEach { problems += "${cert.id}: unknown topic '$it'" }
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
      topicById[id]?.requires?.forEach { visit(it, path + id) }
      state[id] = 2
    }
    topics.forEach { visit(it.id, emptyList()) }
    return problems
  }

  /** The given topics plus everything they depend on, transitively. */
  fun withPrerequisites(ids: Collection<String>): Set<String> {
    val out = mutableSetOf<String>()
    val stack = ids.filter { it in topicById }.toMutableList()
    while (stack.isNotEmpty()) {
      val id = stack.removeAt(stack.lastIndex)
      if (out.add(id)) stack += topicById.getValue(id).requires.filter { it in topicById }
    }
    return out
  }

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): Curriculum = json.decodeFromString(serializer(), text)
  }
}
