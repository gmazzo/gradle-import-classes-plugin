package io.github.gmazzo.importclasses

import org.gradle.api.Action
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

interface ImportClassesExtension {

    operator fun invoke(dependency: Any, vararg moreDependencies: Any, configure: Action<Spec>)

    interface Spec {

        val repackageTo: Property<String>

        val keepsAndRenames: MapProperty<String, String>

        fun keep(className: String)

        fun keep(className: String, renameTo: String?)

        val filters: SetProperty<String>

        fun include(vararg pattern: String)

        fun exclude(vararg pattern: String)

    }

}
