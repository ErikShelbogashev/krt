package krt

import kotlin.test.Test
import kotlin.test.assertEquals
import krt.domain.render.model.BenchmarkConfig
import krt.domain.render.model.RenderMode
import krt.domain.scene.model.DemoScene

class BenchmarkStateTest {

    @Test
    fun `the badge names the renderer of the benchmark run in progress, else the one chosen`() {
        val state = BenchmarkState()
        assertEquals(RenderMode.CPU, state.shownMode(RenderMode.CPU))
        state.running = BenchmarkConfig(DemoScene.DEPLOY, RenderMode.GPU, 800, 600)
        assertEquals(RenderMode.GPU, state.shownMode(RenderMode.CPU))
        state.running = null
        assertEquals(RenderMode.CPU, state.shownMode(RenderMode.CPU))
    }
}
