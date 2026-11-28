package krt.domain.scene.model

/** Commands from the scene inspector; each mesh part retains its own untouched properties. */
sealed interface ObjectChange {
    fun applyTo(obj: Traceable): Traceable

    data class Emission(val scale: Double) : ObjectChange {
        override fun applyTo(obj: Traceable): Traceable = ObjectOptics.withEmission(obj, scale)
    }

    data class Iterations(val count: Int) : ObjectChange {
        override fun applyTo(obj: Traceable): Traceable = (obj as? Fractal)?.withIterations(count) ?: obj
    }

    data class Extinction(val channel: Channel, val value: Double) : ObjectChange {
        override fun applyTo(obj: Traceable): Traceable {
            val density = ObjectOptics.inside(obj)?.density ?: return obj
            val updated = when (channel) {
                Channel.R -> density.copy(x = value)
                Channel.G -> density.copy(y = value)
                Channel.B -> density.copy(z = value)
            }
            return ObjectOptics.withDensity(obj, updated)
        }
    }

    enum class Channel { R, G, B }
}
