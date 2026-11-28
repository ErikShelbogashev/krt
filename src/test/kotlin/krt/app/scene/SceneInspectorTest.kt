package krt.app.scene

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.KrtApp
import krt.app.shell.KrtTheme
import krt.domain.render.model.RenderProgress
import krt.infra.AppContext
import krt.domain.scene.World
import krt.domain.scene.model.DemoScene
import krt.domain.scene.model.Emissive
import krt.domain.scene.model.ObjectOptics
import krt.domain.scene.model.SceneCatalog
import krt.domain.scene.model.Sphere
import krt.platform.math.Point3

@OptIn(ExperimentalComposeUiApi::class)
class SceneInspectorTest {
    @Test
    fun `the scene tab switches pixel options and edits the selected light`() {
        val world = World(listOf(Sphere(Point3.ZERO, 1.0, Emissive(Point3.ONE, 2.0), "Lamp")))
        var filtered by mutableStateOf(true)
        var stratified by mutableStateOf(false)
        val scene = ImageComposeScene(360, 900, Density(1f)) {
            KrtTheme(true) {
                SceneTab(DemoScene.MATERIALS, {}, SceneCatalog.of(world.objects), "Lamp", {}, 0.5, { _, _ -> },
                    sampling = {
                        SamplingControls(filtered, stratified, { filtered = !filtered }, { stratified = !stratified })
                    },
                    properties = { id ->
                        ObjectProperties(ObjectOptics.selected(world.objects, id), { world.editObject(id, it) })
                    })
            }
        }
        try {
            scene.render().close()
            val root = scene.semanticsOwners.single().rootSemanticsNode
            val switches = nodes(root).filter { SemanticsProperties.ToggleableState in it.config }
            assertEquals(2, switches.size)
            switches.forEach { assertTrue(it.config[SemanticsActions.OnClick].action!!.invoke()) }
            assertTrue(!filtered && stratified)
            val field = nodes(root).single { SemanticsActions.SetText in it.config }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("0.75")))
            assertEquals(0.75, ObjectOptics.selected(world.objects, "Lamp").emission)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `two clicks toggle the live camera even before its preview arrives`() {
        AppContext.create(DemoScene.CLASSIC).use { app ->
            app.camera.imageWidth = 40
            app.camera.imageHeight = 40
            app.frameBuffer.resizeTo(40, 40)
            // Render publications are queued on the EDT. Keep it here until both input events have been handled.
            SwingUtilities.invokeAndWait { checkDelayedPreview(app) }
        }
    }

    private fun checkDelayedPreview(app: AppContext) {
        val scene = ImageComposeScene(1200, 900, Density(1f)) { KrtApp(app) }
        try {
            fun controls() = nodes(scene.semanticsOwners.single().rootSemanticsNode)
            fun switches() = controls().filter { SemanticsProperties.ToggleableState in it.config }
            scene.render().close()
            val tab = controls().single { node ->
                SemanticsActions.OnClick in node.config && SemanticsProperties.Text in node.config &&
                    node.config[SemanticsProperties.Text].any { it.text == "Scene" }
            }
            click(tab)
            scene.render().close()
            assertEquals(2, switches().size)
            switches().forEach { control ->
                click(control)
                click(control)
            }
            assertTrue(app.camera.filterWood)
            assertTrue(!app.camera.stratifiedPixels)

            click(switches().first())
            scene.render().close()
            assertEquals(ToggleableState.Off, switches().first().config[SemanticsProperties.ToggleableState])
            assertEquals(RenderProgress.NONE, app.frameBuffer.progress)
        } finally {
            scene.close()
        }
    }

    private fun click(node: SemanticsNode) = assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())

    private fun nodes(root: SemanticsNode): List<SemanticsNode> = listOf(root) + root.children.flatMap(::nodes)
}
