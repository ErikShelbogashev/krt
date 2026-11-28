pluginManagement {
    repositories {
        if (gradle.startParameter.isOffline) maven(rootDir.resolve("third-party/maven"))
        gradlePluginPortal { (this as MavenArtifactRepository).allowInsecureContinueWhenDisabled.set(true) }
        mavenCentral { allowInsecureContinueWhenDisabled.set(true) }
        google { allowInsecureContinueWhenDisabled.set(true) }
        if (!gradle.startParameter.isOffline) maven(rootDir.resolve("third-party/maven"))
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (gradle.startParameter.isOffline) maven(rootDir.resolve("third-party/maven"))
        mavenCentral { allowInsecureContinueWhenDisabled.set(true) }
        google { allowInsecureContinueWhenDisabled.set(true) }
        if (!gradle.startParameter.isOffline) maven(rootDir.resolve("third-party/maven"))
    }
}

rootProject.name = "krt"
