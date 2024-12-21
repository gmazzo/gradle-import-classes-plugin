package io.github.gmazzo.importclasses

import groovy.lang.Closure
import groovy.lang.MissingMethodException
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
import org.gradle.api.file.DuplicatesStrategy
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
    override fun invoke(
        dependency: Any,
        vararg moreDependencies: Any,
        configure: Action<ImportClassesSpec>,
    ): Unit = with(project) {

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

        val jars = config.incoming.files
        val classesDir = layout.buildDirectory.dir("imported-classes/$name")
        val classes = files()
            .from(provider {
                sync {
                    duplicatesStrategy = DuplicatesStrategy.WARN
                    jars.asFileTree.forEach { from(zipTree(it)) }
                    into(classesDir)
                }
                classesDir
            })
            .builtBy(config)
            .apply { finalizeValueOnRead() }

        dependencies.registerTransform(ImportClassesTransform::class) {
            from.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, JAR_TYPE)
            to.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, discriminator)
            parameters.keepsAndRenames.value(spec.keepsAndRenames)
            parameters.repackageName.value(spec.repackageTo)
            parameters.filters.value(spec.filters)
            parameters.extraOptions.value(spec.extraOptions)
            parameters.includeTransitiveDependencies.value(spec.includeTransitiveDependencies)
        }

        dependencies.add(sourceSet.compileOnlyConfigurationName, jars)

        (sourceSet.output.classesDirs as ConfigurableFileCollection).from(classes)
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

}
