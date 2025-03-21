plugins {
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android) apply false
    id("io.github.gmazzo.importclasses") apply false
    alias(libs.plugins.publicationsReport)
    base
    `maven-publish`
}

val pluginBuild = gradle.includedBuild("plugin")

tasks.build {
    dependsOn(pluginBuild.task(":$name"))
}

tasks.check {
    dependsOn(pluginBuild.task(":$name"))
}

tasks.publish {
    dependsOn(pluginBuild.task(":$name"))
}

tasks.publishToMavenLocal {
    dependsOn(pluginBuild.task(":$name"))
}
