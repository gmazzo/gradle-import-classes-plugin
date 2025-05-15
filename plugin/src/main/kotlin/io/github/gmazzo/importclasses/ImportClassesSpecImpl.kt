package io.github.gmazzo.importclasses

import javax.inject.Inject
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.plugin.use.PluginDependency

internal abstract class ImportClassesSpecImpl @Inject constructor(
    private val project: Project
) : ImportClassesSpec {

    var bound = false

    lateinit var disambiguator: String

    lateinit var importsConfig: Configuration

    lateinit var librariesConfig: Configuration

    lateinit var extractedJars: FileCollection

    override fun dependencies(vararg dependencies: Any) = apply {
        importsConfig.addDependencies(dependencies)
    }

    override fun libraries(vararg dependencies: Any) = apply {
        librariesConfig.addDependencies(dependencies)
    }

    private fun Configuration.addDependencies(toAdd: Array<out Any>) {
        for (dependency in toAdd) {
            dependencies.add(project.dependencies.create(dependency.resolved))
        }
    }

    private val Any.resolved: Any
        get() = when (this) {
            is Provider<*> -> get().resolved
            is ProviderConvertible<*> -> asProvider().get().resolved
            is PluginDependency -> run { "$pluginId:$pluginId.gradle.plugin:$version" }
            else -> this
        }

    override fun repackageTo(repackageTo: String) = apply {
        this.repackageTo.value(repackageTo)
    }

    override fun keep(className: String) =
        keep(className, null)

    override fun keep(className: String, renameTo: String?) = apply {
        if (renameTo != null) {
            keepsAndRenames.put(className, renameTo)

        } else {
            keepsAndRenames.put(
                className, repackageTo
                    .map { repackage -> "${repackage}.${className.substring(className.lastIndexOf('.') + 1)}" }
                    .orElse("")
            )
        }
    }

    override fun include(vararg pattern: String) = apply {
        filters.addAll(*pattern)
    }

    override fun exclude(vararg pattern: String) = apply {
        filters.addAll(pattern.map { "!$it" })
    }

    override fun option(vararg option: String) = apply {
        extraOptions.addAll(*option)
    }

}
