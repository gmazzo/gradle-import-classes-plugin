package io.github.gmazzo.importclasses

import org.gradle.api.NamedDomainObjectContainer

interface ImportClassesExtension : ImportClassesSpec {

    val specs: NamedDomainObjectContainer<ImportClassesSpec>

}
