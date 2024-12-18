package io.github.gmazzo.importclasses

import groovy.lang.Closure
import groovy.lang.MissingMethodException
import io.github.gmazzo.importclasses.EmptyTransform.Companion.EMPTY_TYPE
import io.github.gmazzo.importclasses.ImportClassesExtension.Spec
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.JAR_TYPE
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.HasConfigurableAttributes
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
    override fun invoke(vararg dependency: Any, configure: Action<Spec>): Unit = with(project) {
        val discriminator = "extractClasses-${Random.nextInt(16 * 8).toHexString()}"

        val deps = dependency.map {
            project.dependencies.create(
                when (it) {
                    is Provider<*> -> it.get()
                    is ProviderConvertible<*> -> it.asProvider().get()
                    else -> it
                }
            ).apply {
                (this as? HasConfigurableAttributes<*>)?.attributes {
                    attribute(ARTIFACT_TYPE_ATTRIBUTE, discriminator)
                }
            }
        }

        val config = configurations.maybeCreate(discriminator).apply {
            isCanBeResolved = true
            isCanBeConsumed = false
            isVisible = false
            attributes {
                attribute(USAGE_ATTRIBUTE, objects.named(JAVA_RUNTIME))
                attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
                attribute(ARTIFACT_TYPE_ATTRIBUTE, EMPTY_TYPE)
            }
            dependencies.addAll(deps)
        }

        val spec = objects.newInstance<SpecImpl>().apply {
            keeps.finalizeValueOnRead()
            repackageTo.finalizeValueOnRead()
            filters.finalizeValueOnRead()

            configure.execute(this)

            check(keeps.get().isNotEmpty()) { "Must call `keep(<classname>)` at least once" }
        }

        val files = config.incoming.files

        dependencies {
            registerTransform(ImportClassesTransform::class) {
                from.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, JAR_TYPE)
                to.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, discriminator)
                parameters.keeps.value(spec.keeps)
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
        val configure =
            args.lastOrNull() as? Closure<*>
                ?: throw MissingMethodException("importClasses", SourceSet::class.java, args)

        invoke(*args.dropLast(1).toTypedArray(), configure = ConfigureUtil.configureUsing(configure))
    }

    internal abstract class SpecImpl : Spec {

        override fun keep(vararg className: String) {
            keeps.addAll(className.toList())
        }

        override fun include(vararg pattern: String) {
            filters.addAll(*pattern)
        }

        override fun exclude(vararg pattern: String) {
            filters.addAll(pattern.map { "!$it" })
        }

    }

}
