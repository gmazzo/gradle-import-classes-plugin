package io.github.gmazzo.importclasses

import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

interface ImportClassesExtension {

    operator fun invoke(vararg dependency: Any, configure: Action<Spec>)

    interface Spec {

        val keeps: SetProperty<String>

        fun keep(vararg className: String)

        val repackageTo: Property<String>

        val filters: SetProperty<String>

        fun include(vararg pattern: String)

        fun exclude(vararg pattern: String)

    }

}
