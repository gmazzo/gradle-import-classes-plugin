plugins {
    alias(libs.plugins.kotlin.jvm)
    id("io.github.gmazzo.importclasses")
    `maven-publish`
    jacoco
}

java.toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.get())

importClasses {
    repackageTo = "io.github.gmazzo.importclasses.demo.imported"
    keep("org.apache.commons.lang3.StringUtils")
    include("**.class")
}

dependencies {
    importClasses(libs.demo.commons.lang3)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.params)
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
