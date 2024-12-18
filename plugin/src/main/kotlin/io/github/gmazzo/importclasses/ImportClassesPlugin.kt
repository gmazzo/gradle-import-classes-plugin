package io.github.gmazzo.importclasses

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.the

class ImportClassesPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        apply(plugin = "java-base")

        the<SourceSetContainer>().configureEach ss@{
            (this as ExtensionAware).extensions.add(
                ImportClassesExtension::class,
                EXTENSION_NAME,
                objects.newInstance<ImportClassesExtensionImpl>(this@ss),
            )
        }
    }

    companion object {
        const val EXTENSION_NAME = "importClasses"
    }

}
