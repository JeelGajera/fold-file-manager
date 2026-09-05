pluginManagement {
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
        google()
        mavenCentral()
    }
}

rootProject.name = "FOLD"

include(":app")
include(":core:design")
include(":core:storage")
include(":core:crypto")
include(":feature:browser")
include(":feature:search")
include(":feature:transfer")
include(":feature:vault")
include(":feature:glyph")
include(":widget")
