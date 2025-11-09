package io.github.gmazzo.importclasses

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

public interface ImportClassesExtension : ImportClassesSpec {

    public val proguardMainClass: Property<String>

    public val proguardJvmArgs: ListProperty<String>

    public val specs: NamedDomainObjectContainer<out ImportClassesSpec>

}
