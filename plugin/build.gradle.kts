@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.buildConfig)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.samReceiver)
    alias(libs.plugins.dokka)
    alias(libs.plugins.axion.release)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.gradle.pluginPublish)
    alias(libs.plugins.publicationsReport)
    `java-test-fixtures`
    `jacoco-report-aggregation`
}

group = "io.github.gmazzo.importclasses"
description = "Imports and repackages dependencies using `Proguard` tool"
version = scmVersion.version

java.toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
samWithReceiver.annotation(HasImplicitReceiver::class.qualifiedName!!)

val originUrl = providers
    .exec { commandLine("git", "remote", "get-url", "origin") }
    .standardOutput.asText.map { it.trim() }

gradlePlugin {
    website = originUrl
    vcsUrl = originUrl

    plugins {
        create("import-classes") {
            id = "io.github.gmazzo.importclasses"
            displayName = name
            implementationClass = "io.github.gmazzo.importclasses.ImportClassesPlugin"
            description = project.description
            tags.addAll("jarjar", "proguard", "dependencies", "repackage", "import", "classes")
        }
    }
}

mavenPublishing {
    publishToMavenCentral("CENTRAL_PORTAL", automaticRelease = true)

    pom {
        name = "${rootProject.name}-${project.name}"
        description = provider { project.description }
        url = originUrl

        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/license/mit/"
            }
        }

        developers {
            developer {
                id = "gmazzo"
                name = id
                email = "gmazzo65@gmail.com"
            }
        }

        scm {
            connection = originUrl
            developerConnection = originUrl
            url = originUrl
        }
    }
}

buildConfig {
    packageName = "io.github.gmazzo.importclasses"
    buildConfigField("PROGUARD_DEFAULT_DEPENDENCY", libs.proguard.map { "${it.group}:${it.name}:${it.version}" })
}

val androidTest by testing.suites.creating(JvmTestSuite::class)

dependencies {
    fun plugin(plugin: Provider<PluginDependency>) =
        plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

    compileOnly(gradleKotlinDsl())
    compileOnly(plugin(libs.plugins.android))
    compileOnly(libs.proguard)

    testFixturesApi(gradleKotlinDsl())
    testFixturesApi(gradleTestKit())
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.junit.params)
    testFixturesApi(libs.proguard)
    testFixturesApi(plugin(libs.plugins.kotlin.jvm))

    "androidTestImplementation"(testFixtures(project))
    "androidTestImplementation"(plugin(libs.plugins.android))
}

testing.suites.withType<JvmTestSuite> {
    useJUnitJupiter()
}

tasks.withType<Test>().configureEach {
    finalizedBy("${name}CodeCoverageReport")
}

tasks.withType<JacocoReport>().configureEach {
    reports.xml.required = true
}

tasks.check {
    dependsOn(testing.suites.withType<JvmTestSuite>())
}

afterEvaluate {
    tasks.named<Jar>("javadocJar") {
        from(tasks.dokkaGeneratePublicationJavadoc)
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    mustRunAfter(tasks.publishPlugins)
}

tasks.publishPlugins {
    enabled = "$version".matches("\\d+(\\.\\d+)+".toRegex())
}

tasks.publish {
    dependsOn(tasks.publishPlugins)
}
