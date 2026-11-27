package krt.domain.scene.model

/**
 * A scene as a file describes it: its [objects] and its [look]; [warnings] say what of the file krt could not
 * draw as written and what it drew instead (ADR 0016).
 */
data class SceneDescription(val objects: List<Traceable>, val look: SceneLook, val warnings: List<String> = emptyList())
