package io.github.gmazzo.importclasses

import org.gradle.api.Action
import org.gradle.api.Project
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
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import proguard.ConfigurationConstants.DONT_NOTE_OPTION
import proguard.ConfigurationConstants.IGNORE_WARNINGS_OPTION

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ImportClassesPluginBaseTest {

    abstract fun testCases(): List<Array<out Any?>>

    @MethodSource("testCases")
    @ParameterizedTest
    fun `plugin can be applied, and classes are resolved`(
        plugin: String,
        dependency: CharSequence,
        classToKeep: String,
        expectedImportedJar: String,
        libraries: Set<String>,
    ): Unit = with(ProjectBuilder.builder().build()) {
        extra["validateSpecsAreBound"] = false
        gradleIssue31862Workaround()

        apply(plugin = "io.github.gmazzo.importclasses")
        apply(plugin = plugin)

        onBeforeEvaluate()

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

        onAfterEvaluate(plugin)
        getTasksByName("tasks", false) //internally it calls project.evaluate()
        project.plugins.withType<ImportClassesPlugin> {
            validateSpecsAreBound()
        }

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

    open fun Project.onBeforeEvaluate() {
    }

    open fun Project.onAfterEvaluate(plugin: String) {
    }

    @JvmInline
    protected value class NonTransitive(val dependency: String) : CharSequence by dependency {
        override fun toString() = dependency
    }

    // TODO workaround for https://github.com/gradle/gradle/issues/31862
    private fun gradleIssue31862Workaround() = ProblemsProgressEventEmitterHolder.init(object : InternalProblems {

        override fun getInternalReporter() = object : InternalProblemReporter {

            override fun report(
                problem: Problem,
                id: OperationIdentifier
            ) {
                TODO("Not yet implemented")
            }

            override fun internalCreate(action: Action<in InternalProblemSpec>): InternalProblem {
                TODO("Not yet implemented")
            }

            override fun create(
                problemId: ProblemId,
                action: Action<in ProblemSpec>
            ): Problem {
                TODO("Not yet implemented")
            }

            override fun report(
                problemId: ProblemId,
                spec: Action<in ProblemSpec>
            ) {
                TODO("Not yet implemented")
            }

            override fun report(problem: Problem) {
                TODO("Not yet implemented")
            }

            override fun report(problems: Collection<Problem?>) {
                TODO("Not yet implemented")
            }

            override fun throwing(
                exception: Throwable,
                problemId: ProblemId,
                spec: Action<in ProblemSpec>
            ): RuntimeException {
                TODO("Not yet implemented")
            }

            override fun throwing(
                exception: Throwable,
                problem: Problem
            ): RuntimeException {
                TODO("Not yet implemented")
            }

            override fun throwing(
                exception: Throwable,
                problems: Collection<Problem?>
            ): RuntimeException {
                TODO("Not yet implemented")
            }

        }

        override fun getAdditionalDataBuilderFactory(): AdditionalDataBuilderFactory {
            TODO("Not yet implemented")
        }

        override fun getInstantiator(): Instantiator {
            TODO("Not yet implemented")
        }

        override fun getProblemBuilder(): InternalProblemBuilder {
            TODO("Not yet implemented")
        }

        override fun getReporter() = getInternalReporter()

    })

}
