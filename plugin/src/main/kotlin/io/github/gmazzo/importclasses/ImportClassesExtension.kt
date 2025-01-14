package io.github.gmazzo.importclasses

import org.gradle.api.Action

interface ImportClassesExtension {

    operator fun invoke(
        dependency: Any,
        vararg moreDependencies: Any,
        configure: Action<ImportClassesSpec>
    ): ImportClassesSpec

}
