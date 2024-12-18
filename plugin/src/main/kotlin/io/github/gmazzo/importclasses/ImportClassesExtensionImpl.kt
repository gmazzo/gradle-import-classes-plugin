package io.github.gmazzo.importclasses

import groovy.lang.Closure
import groovy.lang.MissingMethodException
import io.github.gmazzo.importclasses.ImportClassesExtension.Spec
import io.github.gmazzo.importclasses.ImportClassesPlugin.Companion.EXTENSION_NAME
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.JAR_TYPE
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.util.internal.ConfigureUtil
import javax.inject.Inject
import kotlin.random.Random

internal abstract class ImportClassesExtensionImpl @Inject constructor(
    private val project: Project,
    private val sourceSet: SourceSet,
) : ImportClassesExtension {

    @OptIn(ExperimentalStdlibApi::class)
    override fun invoke(dependency: Any, vararg moreDependencies: Any, configure: Action<Spec>): Unit = with(project) {

        val deps = (sequenceOf(dependency) + moreDependencies).map {
            when (it) {
                is Provider<*> -> it.get()
                is ProviderConvertible<*> -> it.asProvider().get()
                else -> it
            }
        }.map(project.dependencies::create).toList()

        val discriminator = "extractClasses-" + deps.single().let {
            "${it.group}-${it.name}" + when (deps.size) {
                1 -> ""
                else -> "-${Random.nextInt(16 * 8).toHexString()}"
            }
        }

        val config = configurations.maybeCreate(discriminator).apply {
            isCanBeResolved = true
            isCanBeConsumed = false
            isVisible = false
            attributes {
                attribute(USAGE_ATTRIBUTE, objects.named(JAVA_RUNTIME))
                attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
                attribute(ARTIFACT_TYPE_ATTRIBUTE, discriminator)
            }
            dependencies.addAll(deps)
        }

        val spec = objects.newInstance<SpecImpl>().apply {
            keepsAndRenames.finalizeValueOnRead()
            repackageTo.finalizeValueOnRead()
            filters.finalizeValueOnRead()

            configure.execute(this)

            check(keepsAndRenames.get().isNotEmpty()) { "Must call `keep(<classname>)` at least once" }
        }

        val files = config.incoming.files

        dependencies {
            registerTransform(ImportClassesTransform::class) {
                from.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, JAR_TYPE)
                to.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, discriminator)
                parameters.keepsAndRenames.value(spec.keepsAndRenames)
                parameters.repackageName.value(spec.repackageTo)
                parameters.filters.value(spec.filters)
            }

            sourceSet.implementationConfigurationName(files)
        }
        (sourceSet.output.classesDirs as? ConfigurableFileCollection)?.from(provider { files.map(::zipTree) })
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

    internal abstract class SpecImpl : Spec {

        override fun keep(className: String) =
            keep(className, null)

        override fun keep(className: String, renameTo: String?) {
            if (renameTo != null) {
                keepsAndRenames.put(className, renameTo)

            } else {
                keepsAndRenames.put(className, repackageTo
                    .map { repackage -> "${repackage}.${className.substring(className.lastIndexOf('.') + 1)}" }
                    .orElse(""))
            }
        }

        override fun include(vararg pattern: String) {
            filters.addAll(*pattern)
        }

        override fun exclude(vararg pattern: String) {
            filters.addAll(pattern.map { "!$it" })
        }

    }

}
