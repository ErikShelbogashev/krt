package krt.infra

import javax.swing.SwingUtilities
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class AppContextTest {

    @Test
    fun `closing stops the frame being rendered`() {
        val app = AppContext.create(Random(1))
        var published = 0
        app.frameBuffer.onUpdate { published++ }
        app.changeCameraSettings { samplesPerPixel = 50 }

        app.close()
        Thread.sleep(SETTLE_MS)
        SwingUtilities.invokeAndWait {}
        val afterClose = published

        Thread.sleep(OBSERVE_MS)
        SwingUtilities.invokeAndWait {}
        assertEquals(afterClose, published)
    }

    @Test
    fun `closed context renders nothing`() {
        val app = AppContext.create(Random(1))
        var published = 0
        app.frameBuffer.onUpdate { published++ }

        app.close()
        app.redraw()
        Thread.sleep(OBSERVE_MS)
        SwingUtilities.invokeAndWait {}
        assertEquals(0, published)
    }

    private companion object {
        const val SETTLE_MS = 300L
        const val OBSERVE_MS = 1000L
    }
}
