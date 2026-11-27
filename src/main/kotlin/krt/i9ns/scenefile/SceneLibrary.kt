package krt.i9ns.scenefile

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import krt.domain.scene.model.DemoScene
import krt.domain.scene.model.SceneDescription
import krt.i9ns.objfile.ObjFiles
import krt.i9ns.objfile.ObjScene
import krt.i9ns.pbrt.PbrtReader

/**
 * Resource hiding where scene files live (ADR 0016): the demo scenes in the resources under `krt/scenes`, read
 * once and kept, and files of one's own on disk. It reads the bytes; the pure readers make the scenes of them.
 */
class SceneLibrary {

    private val demos = ConcurrentHashMap<DemoScene, SceneDescription>()

    /** The demo [scene], read on first use. */
    fun load(scene: DemoScene): SceneDescription = demos.getOrPut(scene) { read(Resources(ROOT), scene.path) }

    /** The scene in [file]: a pbrt-v4 file, or an OBJ with its MTL and, beside it, its krt showcase files. */
    fun load(file: File): SceneDescription = read(Directory(file.parentFile ?: File(".")), file.name)

    private fun read(source: Source, path: String): SceneDescription =
        if (path.endsWith(".obj", ignoreCase = true)) obj(source, path) else pbrt(source, path)

    private fun pbrt(source: Source, path: String): SceneDescription {
        val base = directoryOf(path)
        val text = source.text(path) ?: error("no scene file $path")
        val files = mutableMapOf<String, ByteArray>()
        var pending = PbrtReader.references(text)
        while (pending.isNotEmpty()) {
            val name = pending.first()
            pending = pending.drop(1)
            if (name in files) continue
            val bytes = source.bytes(base + name) ?: error("no file $name beside $path")
            files[name] = bytes
            if (name.endsWith(".pbrt")) pending = pending + PbrtReader.references(String(bytes))
        }
        return PbrtReader.read(text, files)
    }

    private fun obj(source: Source, path: String): SceneDescription {
        val text = source.text(path) ?: error("no scene file $path")
        val directory = directoryOf(path)
        return ObjScene.read(
            ObjFiles(
                obj = text,
                mtl = source.text(directory + ObjScene.materialLibrary(text, path.substringAfterLast('/'))),
                showcase = source.text(directory + SHOWCASE),
                layout = source.text(directory + LAYOUT),
            ),
        )
    }

    /** Where scene files are read from. */
    private interface Source {
        fun bytes(path: String): ByteArray?

        fun text(path: String): String? = bytes(path)?.toString(Charsets.UTF_8)
    }

    private class Resources(val root: String) : Source {
        override fun bytes(path: String): ByteArray? =
            SceneLibrary::class.java.getResourceAsStream(root + path)?.use { it.readBytes() }
    }

    private class Directory(val root: File) : Source {
        override fun bytes(path: String): ByteArray? = File(root, path).takeIf { it.isFile }?.readBytes()
    }

    /** The directory of [path] with its slash, or nothing for a bare name. */
    private fun directoryOf(path: String): String =
        path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

    private companion object {
        const val ROOT = "/krt/scenes/"
        const val SHOWCASE = "render-showcase.json"
        const val LAYOUT = "scene-layout.json"
    }
}
