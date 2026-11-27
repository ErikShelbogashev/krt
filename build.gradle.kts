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

    testImplementation(kotlin("test"))
    testImplementation(libs.konsist)

    detektPlugins(libs.compose.rules.detekt)
}

tasks.test {
    useJUnitPlatform()
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

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "krt"
            packageVersion = "1.0.0"
        }
    }
}
