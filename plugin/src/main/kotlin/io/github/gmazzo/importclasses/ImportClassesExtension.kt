package io.github.gmazzo.importclasses

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

interface ImportClassesExtension : ImportClassesSpec {

    val proguardMainClass : Property<String>

    val proguardJvmArgs : ListProperty<String>

    val specs: NamedDomainObjectContainer<ImportClassesSpec>

}
