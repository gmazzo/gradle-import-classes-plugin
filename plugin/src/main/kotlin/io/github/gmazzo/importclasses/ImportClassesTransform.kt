package io.github.gmazzo.importclasses

import proguard.ConfigurationConstants.*
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import proguard.Configuration
import proguard.ConfigurationParser
import proguard.ProGuard
import java.io.File
import java.util.Properties

@CacheableTransform
abstract class ImportClassesTransform : TransformAction<ImportClassesTransform.Params> {

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    @get:Classpath
    @get:InputArtifactDependencies
    abstract val inputArtifactDependencies: FileCollection

    override fun transform(outputs: TransformOutputs): Unit = with(parameters) {
        val inputJar = inputArtifact.get().asFile
        val outputJar = outputs.file(inputJar.nameWithoutExtension + "-extracted.jar").absolutePath

        val tempDir = File.createTempFile("importClasses", null).apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

        val mappingFile = repackageName.orNull?.let { repackage ->
            File(tempDir, "proguard-mapping.txt").apply {
                deleteOnExit()
                writeText(buildString {
                    keeps.get().forEach { className ->
                        appendLine("$className -> ${repackage}.${className.substring(className.lastIndexOf('.') + 1)}:")
                    }
                })
            }
        }

        val filesFilter = filters.get().takeUnless { it.isEmpty() }?.joinToString(
            prefix = "(",
            separator = ",",
            postfix = ")",
        ) ?: ""

        try {
            val args = buildList {
                add(FORCE_PROCESSING_OPTION)
                add(DONT_NOTE_OPTION)
                add(DONT_WARN_OPTION)
                add(DONT_OPTIMIZE_OPTION)
                add(DONT_USE_MIXED_CASE_CLASS_NAMES_OPTION)
                repackageName.orNull?.let {
                    add(REPACKAGE_CLASSES_OPTION)
                    add(it)
                    add(ADAPT_RESOURCE_FILE_NAMES_OPTION)
                    add(APPLY_MAPPING_OPTION)
                    add(mappingFile!!.absolutePath)
                }
                keeps.get().forEach {
                    add(KEEP_OPTION)
                    add(ARGUMENT_SEPARATOR_KEYWORD)
                    add(ALLOW_OBFUSCATION_SUBOPTION)
                    add(CLASS_KEYWORD)
                    add(it)
                    add("{ *; }")
                }
                add(INJARS_OPTION)
                add(inputJar.absolutePath)
                inputArtifactDependencies.forEach {
                    add(INJARS_OPTION)
                    add(it.absolutePath)
                }
                add(OUTJARS_OPTION)
                add("$outputJar$filesFilter")
            }

            val config = Configuration()
            ConfigurationParser(args.toTypedArray(), null).parse(config)
            ProGuard(config).execute()

        } finally {
            tempDir.deleteRecursively()
        }
    }

    interface Params : TransformParameters {

        @get:Input
        val keeps: SetProperty<String>

        @get:Input
        @get:Optional
        val repackageName: Property<String>

        @get:Input
        val filters: ListProperty<String>

    }

}
