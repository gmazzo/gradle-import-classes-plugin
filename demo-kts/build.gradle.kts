plugins {
    alias(libs.plugins.kotlin.jvm)
    id("io.github.gmazzo.importclasses")
    `maven-publish`
}

java.toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.get())

sourceSets.main {
    importClasses(libs.demo.jgit) {
        repackageTo = "io.github.gmazzo.importclasses.demo.imported"
        keep("org.eclipse.jgit.ignore.FastIgnoreRule")

        exclude("META-INF/**/module-info.class")
        include("**.class")
        exclude("**.*")
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.params)
    testRuntimeOnly(libs.demo.slf4j)
}

testing.suites.withType<JvmTestSuite> {
    useJUnitJupiter()
}
