dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "gradle-import-classes-plugin"

includeBuild("plugin")
include("demo-kts")
include("demo-groovy")
