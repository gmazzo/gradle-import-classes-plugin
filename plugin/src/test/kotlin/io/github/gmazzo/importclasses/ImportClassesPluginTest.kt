package io.github.gmazzo.importclasses

import org.gradle.api.Action
import org.gradle.api.attributes.LibraryElements.JAR
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory
import org.gradle.api.problems.internal.InternalProblemReporter
import org.gradle.api.problems.internal.InternalProblemSpec
import org.gradle.api.problems.internal.InternalProblems
import org.gradle.api.problems.internal.Problem
import org.gradle.api.problems.internal.ProblemsProgressEventEmitterHolder
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.kotlin.dsl.apply
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
        plugin: String?,
        dependency: String,
        classToKeep: String,
        expectedImportedJar: String,
        libraries: Set<String>,
    ): Unit = with(ProjectBuilder.builder().build()) {
        gradleIssue31862Workaround()

        val disambiguator = dependency.split(':').take(2).joinToString("-")
        val discriminator = "imported-$disambiguator"

        apply(plugin = "io.github.gmazzo.importclasses")
        if (plugin != null) {
            apply(plugin = plugin)
        }

        repositories {
            mavenCentral()
            google()
        }

        val main = the<SourceSetContainer>().maybeCreate("main")

        main.the<ImportClassesExtension>()(dependency) {
            repackageTo.value("org.test.imported")
            keep(classToKeep)
            libraries(libraries)
            extraOptions.value(setOf(DONT_NOTE_OPTION, IGNORE_WARNINGS_OPTION))
        }

        val paths = main.output.classesDirs.files
            .mapTo(linkedSetOf()) { it.toRelativeString(projectDir) }

        assertEquals(
            setOfNotNull(
                "build/classes/java/main",
                "build/classes/groovy/main".takeIf { plugin == "groovy" },
                "build/classes/kotlin/main".takeIf { plugin == "kotlin" },
                "build/imported-classes/$disambiguator",
            ),
            paths,
        )

        val importedClasses = configurations[discriminator].incoming
            .artifactView { attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("$JAR+$discriminator")) }
            .files
            .mapTo(linkedSetOf()) { it.name }

        assertEquals(setOf(expectedImportedJar), importedClasses)
    }

    fun testCases(): List<Array<out Any?>> =
        sequenceOf(null, "java", "java-library", "groovy", "kotlin").flatMap { plugin ->
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
            )
        }.toList()

    // TODO workaround for https://github.com/gradle/gradle/issues/31862
    private fun gradleIssue31862Workaround() = ProblemsProgressEventEmitterHolder.init(object : InternalProblems {

        override fun getInternalReporter() = object : InternalProblemReporter {

            override fun create(action: Action<InternalProblemSpec?>): Problem {
                TODO("Not yet implemented")
            }

            override fun report(problem: Problem) {
            }

            override fun report(problems: Collection<Problem?>) {
            }

            override fun report(problem: Problem, id: OperationIdentifier) {
            }

            override fun throwing(exception: Throwable, problems: Collection<Problem?>) =
                error("Exception: $exception")

            override fun reporting(spec: Action<ProblemSpec?>) {
            }

            override fun throwing(spec: Action<ProblemSpec?>) =
                error("Exception: $spec")

        }

        override fun getAdditionalDataBuilderFactory(): AdditionalDataBuilderFactory {
            TODO("Not yet implemented")
        }

        override fun getReporter() = getInternalReporter()

    })

}
