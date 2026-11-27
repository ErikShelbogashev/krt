package krt.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

/**
 * Правила из AGENTS.md, ADR 0001 и ADR 0008, которые проверяются автоматически.
 */
class ArchitectureTest {

    private val production = Konsist.scopeFromProduction()

    @Test
    fun `dependencies go only down the package layers`() {
        production.assertArchitecture {
            val app = Layer("app", "krt.app..")
            val domain = Layer("domain", "krt.domain..")
            val i9ns = Layer("i9ns", "krt.i9ns..")
            val infra = Layer("infra", "krt.infra..")
            val platform = Layer("platform", "krt.platform..")

            app.dependsOn(domain, i9ns, platform)
            domain.dependsOn(platform)
            i9ns.dependsOn(domain, platform)
            infra.dependsOn(app, domain, i9ns, platform)
            platform.dependsOnNothing()
        }
    }

    @Test
    fun `domain does not depend on Compose`() {
        production.files
            .filter { it.packagee?.name?.startsWith("krt.domain") == true }
            .assertFalse { file -> file.imports.any { it.name.startsWith("androidx.compose") } }
    }

    @Test
    fun `domain models are free of UI, IO and threads`() {
        val forbidden = listOf(
            "androidx.",
            "java.awt.",
            "javax.swing.",
            "java.io.",
            "java.nio.",
            "java.util.concurrent.",
            "kotlinx.coroutines.",
        )
        production.files
            .filter { it.packagee?.name?.matches(Regex("""krt\.domain\.\w+\.model(\..*)?""")) == true }
            .assertFalse { file -> file.imports.any { import -> forbidden.any { import.name.startsWith(it) } } }
    }

    @Test
    fun `domain models do not read the global environment`() {
        val forbidden = listOf(
            "Random.Default",
            "System.currentTimeMillis",
            "System.nanoTime",
            "Instant.now",
            "System.getenv",
        )
        production.files
            .filter { it.packagee?.name?.matches(Regex("""krt\.domain\.\w+\.model(\..*)?""")) == true }
            .assertFalse { file -> forbidden.any { file.text.contains(it) } }
    }

    @Test
    fun `trace kernel is a pure computation`() {
        val forbidden = listOf(
            "krt.app.",
            "krt.i9ns.",
            "krt.infra.",
            "androidx.",
            "java.awt.",
            "javax.swing.",
            "java.io.",
            "java.nio.",
            "java.util.concurrent.",
            "kotlinx.coroutines.",
        )
        kernelFiles().assertFalse { file ->
            file.imports.any { import -> forbidden.any { import.name.startsWith(it) } }
        }
    }

    @Test
    fun `trace kernel reads neither time nor global randomness`() {
        val forbidden = listOf(
            "Random.Default",
            "System.currentTimeMillis",
            "System.nanoTime",
            "Instant.now",
            "System.getenv",
        )
        kernelFiles().assertFalse { file -> forbidden.any { file.text.contains(it) } }
    }

    @Test
    fun `trace kernel has no lazy sequences on the hot path`() {
        kernelFiles().assertFalse { file -> file.text.contains("asSequence") || file.text.contains("sequence {") }
    }

    @Test
    fun `scene model does not depend on the trace kernel`() {
        production.files
            .filter { it.packagee?.name?.matches(Regex("""krt\.domain\.\w+\.model(\..*)?""")) == true }
            .assertFalse { file -> file.imports.any { it.name.startsWith(KERNEL_PACKAGE) } }
    }

    @Test
    fun `operation has a single public method invoke`() {
        production.classes()
            .withNameEndingWith("Op")
            .assertTrue { op ->
                op.functions().filter { it.hasPublicOrDefaultModifier }.map { it.name } == listOf("invoke")
            }
    }

    @Test
    fun `operation does not depend on another operation`() {
        production.classes()
            .withNameEndingWith("Op")
            .assertFalse { op ->
                op.primaryConstructor?.parameters.orEmpty().any { it.type.name.endsWith("Op") }
            }
    }

    @Test
    fun `operations live in app`() {
        production.classes()
            .withNameEndingWith("Op")
            .assertTrue { it.resideInPackage("krt.app..") }
    }

    @Test
    fun `component has at most 8 dependencies`() {
        production.classes()
            .filterNot { it.resideInPackage("krt.infra..") }
            .assertTrue { it.primaryConstructor?.parameters.orEmpty().size <= MAX_DEPENDENCIES }
    }

    @Test
    fun `domain models are immutable`() {
        // ADR 0005: no `var` property in a model, local variables of a computation aside.
        modelFiles().assertFalse { file ->
            file.properties(includeNested = true).any { it.isVar }
        }
    }

    @Test
    fun `domain models do not depend on resources`() {
        modelFiles().assertFalse { file ->
            file.imports.any { it.name.startsWith("krt.domain.") && ".model." !in it.name }
        }
    }

    @Test
    fun `Skia stays with the GPU renderer, the views that draw frames and the platform`() {
        // ADR 0009, 0010: the GPU resource and the frame buffer in krt.domain.render, the viewport and its
        // operations in krt.app.render, conversions in krt.platform, the window's wiring in the root package, and the
        // smoke check that draws frames on the real GPU.
        val allowed = listOf("krt.domain.render", "krt.app.render", "krt.platform.skia", "krt", "krt.smoke")
        production.files
            .filter { file -> file.imports.any { it.name.startsWith("org.jetbrains.skia") } }
            .assertTrue { it.packagee?.name in allowed }
    }

    @Test
    fun `no wildcard imports`() {
        Konsist.scopeFromProject().imports.assertFalse { it.isWildcard }
    }

    private fun modelFiles() =
        production.files.filter { it.packagee?.name?.matches(Regex("""krt\.domain\.\w+\.model(\..*)?""")) == true }

    private fun kernelFiles() = production.files.filter { it.packagee?.name?.startsWith(KERNEL_PACKAGE) == true }

    private companion object {
        // ADR 0008: вычислительное ядро.
        const val KERNEL_PACKAGE = "krt.domain.trace"

        // AGENTS.md, «Пороги»: связей не больше 8 строго. Корень композиции не считается.
        const val MAX_DEPENDENCIES = 8
    }
}
