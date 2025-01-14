@file:OptIn(ExperimentalStdlibApi::class)

package io.github.gmazzo.importclasses

import groovy.lang.Closure
import groovy.lang.MissingMethodException
import io.github.gmazzo.importclasses.ImportClassesPlugin.Companion.EXTENSION_NAME
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.LibraryElements.CLASSES
import org.gradle.api.attributes.LibraryElements.JAR
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.LibraryElements.RESOURCES
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.util.internal.ConfigureUtil
import proguard.ConfigurationConstants.DONT_NOTE_OPTION
import proguard.ConfigurationConstants.DONT_WARN_OPTION
import proguard.ConfigurationConstants.IGNORE_WARNINGS_OPTION
import java.util.*
import javax.inject.Inject

internal abstract class ImportClassesExtensionImpl @Inject constructor(
    private val project: Project,
    private val sourceSet: SourceSet,
) : ImportClassesExtension {

    override fun invoke(
        dependency: Any,
        vararg moreDependencies: Any,
        configure: Action<ImportClassesSpec>,
    ): Unit = with(project) {

        val deps = (sequenceOf(dependency) + moreDependencies)
            .map {
                when (it) {
                    is Provider<*> -> it.get()
                    is ProviderConvertible<*> -> it.asProvider().get()
                    else -> it
                }
            }
            .map(project.dependencies::create)
            .toSortedSet(compareBy { it.discriminatorPart })

        val disambiguator = computeDisambiguator(deps)
        val discriminator = "imported-$disambiguator"
        val jarElements: LibraryElements = objects.named("$JAR+$discriminator")
        val classesElements: LibraryElements = objects.named("$CLASSES+$discriminator")
        val resourcesElements: LibraryElements = objects.named("$RESOURCES+$discriminator")

        fun Configuration.configureAttrs() = attributes {
            attribute(USAGE_ATTRIBUTE, objects.named(JAVA_RUNTIME))
            attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
            attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(JAR))
        }

        val config = configurations.maybeCreate(discriminator).apply {
            isCanBeResolved = true
            isCanBeConsumed = false
            isVisible = false
            configureAttrs()
            dependencies.addAll(deps)
        }

        val spec = objects.newInstance<ImportClassesSpecImpl>().apply {

            keepsAndRenames
                .finalizeValueOnRead()

            repackageTo
                .finalizeValueOnRead()

            filters
                .finalizeValueOnRead()

            extraOptions
                .apply {
                    if (!logger.isDebugEnabled) addAll(
                        DONT_NOTE_OPTION,
                        if (logger.isInfoEnabled) IGNORE_WARNINGS_OPTION else DONT_WARN_OPTION
                    )
                }
                .finalizeValueOnRead()

            libraries
                .finalizeValueOnRead()

            // excludes by default all known resources related to the module build process
            exclude(
                "META-INF/LICENSE.txt",
                "META-INF/MANIFEST.MF",
                "META-INF/*.kotlin_module",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/maven/**",
                "META-INF/versions/*/module-info.class"
            )

            configure.execute(this)

            check(keepsAndRenames.get().isNotEmpty()) { "Must call `keep(<classname>)` at least once" }
        }

        dependencies.registerTransform(ImportClassesTransform::class) {
            val libraries = configurations
                .detachedConfiguration(*spec.libraries.get().map(project.dependencies::create).toTypedArray())
                .configureAttrs()

            from.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(JAR))
            to.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, jarElements)
            parameters.inJARs.from(config)
            parameters.libraryJARs.from(libraries)
            parameters.keepsAndRenames.value(spec.keepsAndRenames)
            parameters.repackageName.value(spec.repackageTo)
            parameters.filters.value(spec.filters)
            parameters.extraOptions.value(spec.extraOptions)
        }

        dependencies.registerTransform(ExtractJARTransform::class) {
            from.attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, jarElements)
            to.attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, classesElements)
            parameters.forResources = false
        }

        dependencies.registerTransform(ExtractJARTransform::class) {
            from.attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, jarElements)
            to.attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, resourcesElements)
            parameters.forResources = true
        }

        fun extractedFiles(elements: LibraryElements) = config.incoming
            .artifactView { attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, elements) }
            .files

        // a task is required when dependencies are generated by tasks of the build, since it's exposed as an outgoing variant artifact
        val extractClasses =
            tasks.register<Sync>("extract${disambiguator.replaceFirstChar { it.uppercase() }}ImportedClasses") {
                from(extractedFiles(classesElements))
                into(layout.buildDirectory.dir("imported-classes/$disambiguator"))
                duplicatesStrategy = DuplicatesStrategy.WARN
            }

        dependencies.add(sourceSet.compileOnlyConfigurationName, extractedFiles(jarElements))
        (sourceSet.output.classesDirs as ConfigurableFileCollection).from(extractClasses)
        sourceSet.resources.srcDir(extractedFiles(resourcesElements))
    }

    /**
     * For Groovy support
     */
    @Suppress("unused")
    fun call(vararg args: Any) {
        fun missingMethod(): Nothing = throw MissingMethodException(EXTENSION_NAME, SourceSet::class.java, args)

        if (args.size < 2) missingMethod()
        val configure = args.last() as? Closure<*> ?: missingMethod()

        invoke(
            dependency = args[0],
            moreDependencies = args.drop(1).dropLast(1).toTypedArray(),
            configure = ConfigureUtil.configureUsing(configure)
        )
    }

    private val Dependency.discriminatorPart
        get() = "$group-$name"

    private fun computeDisambiguator(dependencies: SortedSet<Dependency>) = buildString {
        check(dependencies.isNotEmpty()) { "At least one dependency is required" }
        append(dependencies.first().discriminatorPart)
        if (dependencies.size > 1) {
            append('-')
            append(dependencies.drop(1).map { it.discriminatorPart }.hashCode().toHexString())
        }
    }

}
