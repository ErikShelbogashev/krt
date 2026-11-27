import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
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
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons.core)
    implementation(libs.filekit.dialogs.compose)

    testImplementation(kotlin("test"))
    testImplementation(libs.konsist)
}

tasks.test {
    useJUnitPlatform()
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
