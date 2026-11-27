package krt.domain.scene.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import krt.platform.math.Color
import krt.platform.math.Point3
import krt.platform.math.Ray

class EmissionTest {

    @Test
    fun `black bodies glow red when cool and blue when hot`() {
        val candle = Blackbody.color(1900.0)
        val sky = Blackbody.color(12000.0)
        assertTrue(candle.x > candle.y && candle.y > candle.z, "candle $candle")
        assertTrue(sky.z > sky.x, "sky $sky")
        listOf(candle, sky, Blackbody.color(6500.0)).forEach { assertEquals(1.0, maxOf(it.x, it.y, it.z), 1e-12) }
        val daylight = Blackbody.color(6500.0)
        assertTrue(minOf(daylight.x, daylight.y, daylight.z) > 0.7, "daylight $daylight")
    }

    @Test
    fun `a light by temperature glows its black-body colour`() {
        val lamp = Emissive.kelvin(2700.0, 5.0)
        assertEquals(Blackbody.color(2700.0) * 5.0, lamp.emitted(0.0, 0.0, Point3.ZERO))
        assertTrue(lamp.emits)
    }

    @Test
    fun `a glowing pattern gives off its pattern's colour and scatters nothing`() {
        val pattern = Pattern(PatternKind.NOISE, Color(1.0, 0.2, 0.0), Color(0.1, 0.0, 0.0), 3.0)
        val lava = GlowingPattern(pattern, 4.0)
        val p = Point3(0.2, 0.7, -0.4)
        assertEquals(pattern.colorAt(p) * 4.0, lava.emitted(0.0, 0.0, p))
        val ray = Ray(Point3(0.0, 1.0, 0.0), Point3(0.0, -1.0, 0.0))
        assertNull(lava.scatter(ray, Hit.create(Point3.ZERO, 1.0, ray, Point3(0.0, 1.0, 0.0), lava), Random(1)))
        assertTrue(lava.emits)
        assertTrue(!Lambert(Color.ONE).emits)
    }
}
