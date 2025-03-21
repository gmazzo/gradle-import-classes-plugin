pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
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
