package krt.domain.trace

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krt.domain.scene.model.PatternKind
import org.jetbrains.skia.RuntimeEffect

class GpuShaderSourceTest {

    private fun read(path: String): String =
        requireNotNull(GpuShaderSourceTest::class.java.getResource(path)) { "missing $path" }.readText()

    @Test
    fun `the constants the shader shares with the packing come from the packing`() {
        val constants = GpuShaderSource.constants()
        assertTrue("const float MATERIAL_VALUES = ${GpuScenePacking.MATERIAL_VALUES.toDouble()};" in constants)
        assertTrue("const float PRIMITIVE_VALUES = ${GpuScenePacking.PRIMITIVE_VALUES.toDouble()};" in constants)
        assertTrue("const float GLOWING = ${GpuScenePacking.GLOWING.toDouble()};" in constants)
        assertTrue("const float FRACTAL = ${GpuScenePacking.FRACTAL.toDouble()};" in constants)
        assertTrue("const int LEAF_SIZE = ${Bvh.LEAF_SIZE};" in constants)
        PatternKind.entries.forEach { assertTrue("const float ${it.name} = ${it.ordinal.toDouble()};" in constants) }
    }

    @Test
    fun `no part of the shader declares a constant the packing generates`() {
        val generated = Regex("""const \w+ (\w+) =""").findAll(GpuShaderSource.constants()).map { it.groupValues[1] }
        GpuShaderSource.PARTS.forEach { path ->
            val text = read(path)
            generated.forEach { name ->
                assertTrue(!Regex("""const \w+ $name\b""").containsMatchIn(text), "$path declares $name by hand")
            }
        }
    }

    @Test
    fun `every shader part in the resources is joined, and the whole compiles`() {
        val onDisk = File("src/main/resources").walk().filter { it.extension == "sksl" }
            .map { "/" + it.relativeTo(File("src/main/resources")).invariantSeparatorsPath }.toSet()
        assertEquals(onDisk, (GpuShaderSource.PARTS + GpuShaderSource.DISPLAY_PARTS).toSet())
        RuntimeEffect.makeForShader(GpuShaderSource.assemble(::read)).close()
        RuntimeEffect.makeForShader(GpuShaderSource.windowDisplay(::read)).close()
    }
}
