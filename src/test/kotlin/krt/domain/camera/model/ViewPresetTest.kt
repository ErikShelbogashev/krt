package krt.domain.camera.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import krt.domain.camera.Camera
import krt.platform.math.Point3

class ViewPresetTest {

    @Test
    fun `a camera set to a preset matches it, the top view too`() {
        ViewPreset.entries.forEach { preset ->
            val camera = Camera.default().apply { lookFrom(preset.origin, preset.target) }
            assertEquals(preset, camera.snapshot().preset, "$preset")
        }
    }

    @Test
    fun `the default preset is the view the camera starts with`() {
        assertEquals(ViewPreset.DEFAULT, Camera.default().snapshot().preset)
    }

    @Test
    fun `a moved or turned camera matches no preset`() {
        val moved = Camera.default().apply { moveForward() }
        val turned = Camera.default().apply { adjustYaw(0.1) }
        assertNull(moved.snapshot().preset)
        assertNull(turned.snapshot().preset)
    }

    @Test
    fun `presets look at their target`() {
        val camera = Camera.default().apply { lookFrom(ViewPreset.FRONT.origin, ViewPreset.FRONT.target) }
        assertEquals(java.awt.Point(200, 200), camera.project(Point3(0.0, 1.0, 0.0)))
    }
}
