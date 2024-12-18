package io.github.gmazzo.importclasses

import io.github.gmazzo.importclasses.EmptyTransform.Companion.EMPTY_TYPE
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.JAR_TYPE
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.kotlin.dsl.the

class ImportClassesPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        apply(plugin = "java-base")

        dependencies.registerTransform(EmptyTransform::class) {
            from.attribute(ARTIFACT_TYPE_ATTRIBUTE, JAR_TYPE)
            to.attribute(ARTIFACT_TYPE_ATTRIBUTE, EMPTY_TYPE)
        }

        the<SourceSetContainer>().configureEach ss@{
            (this as ExtensionAware).extensions.add(
                ImportClassesExtension::class,
                "importClasses",
                objects.newInstance<ImportClassesExtensionImpl>(this@ss),
            )
        }
    }

}
