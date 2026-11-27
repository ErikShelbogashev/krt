package krt.domain.scene.model

import krt.platform.math.Color
import krt.platform.math.Point3

/** Light from where rays leave the scene: a gradient from [horizon] straight ahead to [zenith] straight up. */
data class Sky(val horizon: Color, val zenith: Color) {

    fun color(direction: Point3): Color {
        val t = 0.5 * (direction.unit().y + 1.0)
        return horizon * (1.0 - t) + zenith * t
    }

    companion object {
        val DAYLIGHT = Sky(Color(1.0, 1.0, 1.0), Color(0.5, 0.7, 1.0))
    }
}
