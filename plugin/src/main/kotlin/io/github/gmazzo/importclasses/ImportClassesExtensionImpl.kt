@file:OptIn(ExperimentalStdlibApi::class)

package io.github.gmazzo.importclasses

import javax.inject.Inject
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.SourceSet

internal abstract class ImportClassesExtensionImpl @Inject constructor(
    project: Project,
) : ImportClassesExtension,
    ImportClassesSpecImpl(project) {

    abstract val specsByTarget: MapProperty<String, List<ImportClassesSpecImpl>>

    override fun getName() = SourceSet.MAIN_SOURCE_SET_NAME

    abstract override val specs: NamedDomainObjectContainer<ImportClassesSpecImpl>

    init {
        specs.add(this)
        specsByTarget.putAll(project.provider { specs.groupBy { it.intoSourceSet.get() }})
        specsByTarget.finalizeValueOnRead()
    }

}
