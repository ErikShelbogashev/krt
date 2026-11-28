import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.detekt)
    alias(libs.plugins.jmh)
}

group = "ru.erxxshll6y.krt"
version = "1.0-SNAPSHOT"

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.filekit.dialogs.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.konsist)

    detektPlugins(libs.compose.rules.detekt)
}

tasks.test {
    useJUnitPlatform()
    // The offscreen GPU tests reach Metal through the foreign function API.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // `-Dkrt.writeScenes=true` writes the showcase scene files from their builders (ShowcaseScenesTest).
    systemProperty("krt.writeScenes", System.getProperty("krt.writeScenes") ?: "false")
}

// The GPU smoke check on the real GPU (Metal), off screen; not in `check`, CI has no GPU (ADR 0010).
val gpuSmoke: SourceSet = sourceSets.create("gpuSmoke") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath + sourceSets.main.get().runtimeClasspath
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileGpuSmokeKotlin") {
    // The Metal command queue is made through the foreign function API, final since Java 22.
    compilerOptions.jvmTarget = JvmTarget.JVM_22
}

tasks.named<JavaCompile>("compileGpuSmokeJava") {
    sourceCompatibility = "22"
    targetCompatibility = "22"
}

tasks.register<JavaExec>("gpuSmoke") {
    group = "verification"
    description = "Draws every built-in scene on the real GPU and fails on slow steps, Metal errors or bad frames."
    classpath = gpuSmoke.runtimeClasspath
    mainClass = "krt.smoke.GpuSmokeKt"
    workingDir = projectDir
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true")
    providers.gradleProperty("scene").orNull?.let { args(it) }
}

jmh {
    jmhVersion = libs.versions.jmh
    warmupIterations = 2
    iterations = 3
    fork = 1
    // Lets the benchmarks find samples/ without depending on the working directory.
    jvmArgsAppend.add("-Dkrt.projectDir=${project.projectDir}")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(file("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline.xml")
}

compose.desktop {
    application {
        mainClass = "krt.MainKt"
        // The GPU mode reaches Metal through the foreign function API (MetalContext).
        jvmArgs("--enable-native-access=ALL-UNNAMED")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "krt"
            packageVersion = "1.0.0"
        }
    }
}
