package krt.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

/**
 * Правила из AGENTS.md и ADR 0001, которые проверяются автоматически.
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
    fun `no wildcard imports`() {
        Konsist.scopeFromProject().imports.assertFalse { it.isWildcard }
    }

    private companion object {
        // AGENTS.md, «Пороги»: связей не больше 8 строго. Корень композиции не считается.
        const val MAX_DEPENDENCIES = 8
    }
}
