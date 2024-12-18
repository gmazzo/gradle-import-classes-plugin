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
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

testing.suites.withType<JvmTestSuite> {
    useJUnitJupiter()
}

tasks.register<Sync>("aaa") {
    from(zipTree(tasks.jar.map { it.archiveFile }))
    into(layout.buildDirectory.dir("aaa"))
}