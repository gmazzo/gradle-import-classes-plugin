package io.github.gmazzo.importclasses

import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportClassesPluginTest : ImportClassesPluginBaseTest() {

   override fun testCases(): List<Array<out Any?>> =
        sequenceOf("java", "java-library", "groovy", "kotlin").flatMap { plugin ->
            sequenceOf(
                arrayOf(
                    plugin,
                    "org.apache.commons:commons-lang3:3.14.0",
                    "org.apache.commons.lang3.StringUtils",
                    "commons-lang3-3.14.0-imported.jar",
                    emptySet<String>(),
                ),
                arrayOf(
                    plugin,
                    "org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r",
                    "org.eclipse.jgit.ignore.FastIgnoreRule",
                    "org.eclipse.jgit-6.10.0.202406032230-r-imported.jar",
                    setOf("org.slf4j:slf4j-api:2.0.16"),
                ),
                arrayOf(
                    plugin,
                    NonTransitive("com.android.tools.build:gradle:8.7.3"),
                    "com.android.build.gradle.internal.dependency.AarToClassTransform",
                    "gradle-8.7.3-imported.jar",
                    emptySet<String>(),
                ),
            )
        }.toList()

}
