plugins {
    alias(libs.plugins.kotlin.jvm)
    id("io.github.gmazzo.importclasses")
    `maven-publish`
    jacoco
}

java.toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.get())

importClasses {
    dependencies(libs.demo.commons.lang3)
    repackageTo = "io.github.gmazzo.importclasses.demo.imported"
    keep("org.apache.commons.lang3.StringUtils")
    include("**.class")

    specs.create("json") {
        intoSourceSet = SourceSet.MAIN_SOURCE_SET_NAME
        dependencies(libs.demo.groovy)
        repackageTo = "io.github.gmazzo.importclasses.demo.imported.json"
        keep("groovy.json.JsonSlurper")
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.params)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.launcher)
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
