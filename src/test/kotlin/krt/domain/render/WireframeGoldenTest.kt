package krt.domain.render

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import krt.domain.camera.Camera
import krt.domain.scene.World
import krt.domain.scene.model.Dielectric
import krt.domain.scene.model.Lambert
import krt.domain.scene.model.Metal
import krt.domain.scene.model.Sphere
import krt.domain.scene.model.Triangle
import krt.platform.math.Color
import krt.platform.math.Point3

/**
 * Characterization test: wireframe rendering is deterministic, so the whole frame is compared
 * pixel by pixel with a golden image. A changed golden image means changed behavior.
 */
class WireframeGoldenTest {

    @Test
    fun `wireframe frame matches golden image`() {
        val world = World()
        world.add(Sphere(Point3(3.0, 0.0, 0.0), 1.0, Lambert(Color(0.5, 0.5, 0.5))))
        world.add(Sphere(Point3(5.0, 1.0, 2.0), 0.5, Metal(Color(0.7, 0.6, 0.5), 0.1)))
        world.add(
            Triangle(Point3(4.0, -1.0, -2.0), Point3(4.0, -1.0, -1.0), Point3(4.0, 0.5, -1.5), Dielectric(1.5))
        )
        val camera = Camera(
            origin = Point3(0.0, 0.5, 0.0),
            target = Point3(1.0, 0.5, 0.0),
            vup = Point3(0.0, 1.0, 0.0),
            fov = 70.0,
            aperture = 0.0,
            focusDist = 1.0,
            imageWidth = 96,
            imageHeight = 64,
        )
        val frame = Frame(camera.imageWidth, camera.imageHeight)
        var updates = 0

        RenderController(50L, 50).use { controller ->
            controller.toggleWireframeMode()
            controller.renderImage(frame, RayTracer(world, 5), camera) { updates++ }
        }

        assertEquals(1, updates)
        assertMatchesGolden("wireframe.png", frame)
    }
}

internal fun assertMatchesGolden(name: String, frame: Frame) {
    val actual = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_ARGB)
    actual.setRGB(0, 0, frame.width, frame.height, frame.pixels, 0, frame.width)
    val golden = File("src/test/resources/golden/$name")
    if (!golden.exists()) {
        golden.parentFile.mkdirs()
        ImageIO.write(actual, "png", golden)
        fail("Golden image $golden did not exist and was written; review it and re-run")
    }
    val expected = ImageIO.read(golden)
    assertEquals(expected.width, actual.width, "width")
    assertEquals(expected.height, actual.height, "height")
    var differing = 0
    for (y in 0 until actual.height) for (x in 0 until actual.width) {
        if (expected.getRGB(x, y) != actual.getRGB(x, y)) differing++
    }
    assertEquals(0, differing, "pixels differing from $golden")
}
