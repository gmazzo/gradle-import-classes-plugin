plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.samReceiver)
    alias(libs.plugins.gradle.pluginPublish)
    alias(libs.plugins.publicationsReport)
    jacoco
}

group = "io.github.gmazzo.importclasses"
description = "Gradle Import Classes Plugin"
version = providers
    .exec { commandLine("git", "describe", "--tags", "--always") }
    .standardOutput.asText.get().trim().removePrefix("v")

java.toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
samWithReceiver.annotation(HasImplicitReceiver::class.qualifiedName!!)

gradlePlugin {
    website.set("https://github.com/gmazzo/gradle-import-classes-plugin")
    vcsUrl.set("https://github.com/gmazzo/gradle-import-classes-plugin")

    plugins {
        create("import-classes") {
            id = "io.github.gmazzo.importclasses"
            displayName = name
            implementationClass = "io.github.gmazzo.importclasses.ImportClassesPlugin"
            description = "Imports and repackages dependencies using `Proguard` tool"
            tags.addAll("jarjar", "proguard", "dependencies", "repackage", "import", "classes")
        }
    }
}

dependencies {
    fun plugin(plugin: Provider<PluginDependency>) =
        plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

    compileOnly(gradleKotlinDsl())
    testImplementation(gradleKotlinDsl())
    testImplementation(gradleTestKit())
    testImplementation(plugin(libs.plugins.kotlin.jvm))

    compileOnly(plugin(libs.plugins.kotlin.jvm))
    implementation(libs.proguard)
}

testing.suites.withType<JvmTestSuite> {
    useJUnitJupiter()
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports.xml.required = true
}

tasks.publish {
    dependsOn(tasks.publishPlugins)
}
