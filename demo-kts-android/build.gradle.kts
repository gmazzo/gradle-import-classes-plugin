plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlin.android)
    id("io.github.gmazzo.importclasses")
    `maven-publish`
    jacoco
}

java.toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.get())

android {
    compileSdk = 35
    namespace = "io.github.gmazzo.importclasses.android"
}

importClasses {
    dependencies(libs.demo.commons.lang3)
    repackageTo = "io.github.gmazzo.importclasses.demo.imported"
    keep("org.apache.commons.lang3.StringUtils")
    include("**.class")

    specs.create("debug") {
        dependencies(libs.demo.groovy)
        repackageTo = "io.github.gmazzo.importclasses.demo.imported.debug"
        keep("groovy.json.JsonSlurper")
        include("**.class")
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.params)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
