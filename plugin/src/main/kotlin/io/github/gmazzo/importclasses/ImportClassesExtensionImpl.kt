@file:OptIn(ExperimentalStdlibApi::class)

package io.github.gmazzo.importclasses

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.gradle.kotlin.dsl.domainObjectContainer
import javax.inject.Inject

internal abstract class ImportClassesExtensionImpl private constructor(
    override val specs: NamedDomainObjectContainer<ImportClassesSpec>,
) : ImportClassesExtension,
    ImportClassesSpec by specs.create(MAIN_SOURCE_SET_NAME) {

    @Inject
    constructor(objects: ObjectFactory) : this(objects.domainObjectContainer(ImportClassesSpec::class))

}
