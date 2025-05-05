package io.github.gmazzo.importclasses

import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.attributes.LibraryElements.JAR
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.problems.Problem
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory
import org.gradle.api.problems.internal.InternalProblem
import org.gradle.api.problems.internal.InternalProblemBuilder
import org.gradle.api.problems.internal.InternalProblemReporter
import org.gradle.api.problems.internal.InternalProblemSpec
import org.gradle.api.problems.internal.InternalProblems
import org.gradle.api.problems.internal.ProblemsProgressEventEmitterHolder
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.reflect.Instantiator
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.the
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import proguard.ConfigurationConstants.DONT_NOTE_OPTION
import proguard.ConfigurationConstants.IGNORE_WARNINGS_OPTION

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportClassesPluginTest {

    @MethodSource("testCases")
    @ParameterizedTest
    fun `plugin can be applied, and classes are resolved`(
        plugin: String,
        dependency: CharSequence,
        classToKeep: String,
        expectedImportedJar: String,
        libraries: Set<String>,
    ): Unit = with(ProjectBuilder.builder().build()) {
        apply(plugin = "io.github.gmazzo.importclasses")
        apply(plugin = plugin)

        repositories {
            mavenCentral()
            google()
        }

        val main = the<SourceSetContainer>().maybeCreate("main")

        configure<ImportClassesExtension> {
            repackageTo.value("org.test.imported")
            keep(classToKeep)
            extraOptions.value(setOf(DONT_NOTE_OPTION, IGNORE_WARNINGS_OPTION))
        }

        dependencies {
            "importClasses"(dependency).apply {
                (this as ModuleDependency).isTransitive = dependency !is NonTransitive
            }
            libraries.forEach { "importClassesLibraries"(it) }
        }

        project.getTasksByName("tasks", false) //internally it calls project.evaluate()

        val paths = main.output.classesDirs.files
            .mapTo(linkedSetOf()) { it.toRelativeString(projectDir) }

        assertEquals(
            setOfNotNull(
                "build/classes/java/main",
                "build/classes/groovy/main".takeIf { plugin == "groovy" },
                "build/classes/kotlin/main".takeIf { plugin == "kotlin" },
                "build/imported/main/classes",
            ),
            paths,
        )

        val importedClasses = configurations["importClasses"].incoming
            .artifactView {
                attributes.attribute(
                    LIBRARY_ELEMENTS_ATTRIBUTE,
                    objects.named("$JAR+imported-main")
                )
            }
            .files
            .mapTo(linkedSetOf()) { it.name }

        assertEquals(setOf(expectedImportedJar), importedClasses)
    }

    fun testCases(): List<Array<out Any?>> =
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

    @JvmInline
    private value class NonTransitive(val dependency: String) : CharSequence by dependency {
        override fun toString() = dependency
    }

}
