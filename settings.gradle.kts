pluginManagement {
    repositories {
        maven(rootDir.resolve("third-party/maven"))
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(rootDir.resolve("third-party/maven"))
    }
}

rootProject.name = "krt"
