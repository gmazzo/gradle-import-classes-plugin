pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "gradle-import-classes-plugin"

includeBuild("plugin")
include("demo-kts")
include("demo-kts-android")
include("demo-groovy")
include("demo-groovy-android")
