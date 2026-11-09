pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "ListAnything"

// App module: navigation, DI entry point, theme host.
include(":app")

// Core modules. Dependency direction: feature -> core:ui / core:domain / core:data, core:data -> core:domain.
include(":core:domain") // Pure Kotlin: models and repository interfaces
include(":core:data")   // Firebase implementations of the repositories
include(":core:ui")     // Theme and shared Compose components

// Feature modules. Features never depend on each other.
include(":feature:auth")   // Google sign-in
include(":feature:spaces") // Spaces, members, invites, the lists inside a space
include(":feature:lists")  // Items, check-off, photos, map
