plugins {
    alias(libs.plugins.kotlin.jvm)
    id("io.github.gmazzo.importclasses")
    `maven-publish`
}

java.toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.get())

sourceSets.main {
    importClasses(libs.demo.common.string) {
        repackageTo = "io.github.gmazzo.importclasses.demo.imported"
        keep("org.apache.commons.lang3.StringUtils")

        exclude("META-INF/**/module-info.class")
        include("**.class")
        exclude("**.*")
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.params)
}

testing.suites.withType<JvmTestSuite> {
    useJUnitJupiter()
}
