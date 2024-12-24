@file:OptIn(ExperimentalStdlibApi::class)

package io.github.gmazzo.importclasses

import groovy.lang.Closure
import groovy.lang.MissingMethodException
import io.github.gmazzo.importclasses.ImportClassesPlugin.Companion.EXTENSION_NAME
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.CLASSES
import org.gradle.api.attributes.LibraryElements.JAR
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.LibraryElements.RESOURCES
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.util.internal.ConfigureUtil
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

        val discriminator = computeDiscriminator(deps)

        val config = configurations.maybeCreate(discriminator).apply {
            isCanBeResolved = true
            isCanBeConsumed = false
            isVisible = false
            attributes {
                attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
                attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(JAR))
            }
            dependencies.addAll(deps)
        }

        val spec = objects.newInstance<ImportClassesSpecImpl>().apply {
            keepsAndRenames.finalizeValueOnRead()
            repackageTo.finalizeValueOnRead()
            filters.finalizeValueOnRead()
            extraOptions.finalizeValueOnRead()
            includeTransitiveDependencies.convention(false).finalizeValueOnRead()

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
            from.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(JAR))
            to.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("$JAR+$discriminator"))
            parameters.keepsAndRenames.value(spec.keepsAndRenames)
            parameters.repackageName.value(spec.repackageTo)
            parameters.filters.value(spec.filters)
            parameters.extraOptions.value(spec.extraOptions)
            parameters.includeTransitiveDependencies.value(spec.includeTransitiveDependencies)
        }

        dependencies.registerTransform(ExtractJARTransform::class) {
            from.attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("$JAR+$discriminator"))
            to.attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("$CLASSES+$discriminator"))
            parameters.forResources = false
        }

        dependencies.registerTransform(ExtractJARTransform::class) {
            from.attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("$JAR+$discriminator"))
            to.attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("$RESOURCES+$discriminator"))
            parameters.forResources = true
        }

        fun extractedFiles(ofType: String) = config.incoming.artifactView {
            attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("$ofType+$discriminator"))
        }.files

        dependencies.add(sourceSet.compileOnlyConfigurationName, extractedFiles(JAR))
        (sourceSet.output.classesDirs as ConfigurableFileCollection).from(extractedFiles(CLASSES))
        sourceSet.resources.srcDir(extractedFiles(RESOURCES))
    }

    /**
     * For Groovy support
     */
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

    private fun computeDiscriminator(dependencies: SortedSet<Dependency>) = buildString {
        check(dependencies.isNotEmpty()) { "At least one dependency is required" }
        append("imported-")
        append(dependencies.first().discriminatorPart)
        if (dependencies.size > 1) {
            append('-')
            append(dependencies.drop(1).map { it.discriminatorPart }.hashCode().toHexString())
        }
    }

}
