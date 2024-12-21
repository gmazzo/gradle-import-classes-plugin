package io.github.gmazzo.importclasses

import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import proguard.Configuration
import proguard.ConfigurationConstants.ADAPT_CLASS_STRINGS_OPTION
import proguard.ConfigurationConstants.ADAPT_RESOURCE_FILE_CONTENTS_OPTION
import proguard.ConfigurationConstants.ADAPT_RESOURCE_FILE_NAMES_OPTION
import proguard.ConfigurationConstants.APPLY_MAPPING_OPTION
import proguard.ConfigurationConstants.CLASS_KEYWORD
import proguard.ConfigurationConstants.DONT_NOTE_OPTION
import proguard.ConfigurationConstants.DONT_OPTIMIZE_OPTION
import proguard.ConfigurationConstants.DONT_USE_MIXED_CASE_CLASS_NAMES_OPTION
import proguard.ConfigurationConstants.DONT_WARN_OPTION
import proguard.ConfigurationConstants.FORCE_PROCESSING_OPTION
import proguard.ConfigurationConstants.INJARS_OPTION
import proguard.ConfigurationConstants.KEEP_OPTION
import proguard.ConfigurationConstants.LIBRARYJARS_OPTION
import proguard.ConfigurationConstants.OUTJARS_OPTION
import proguard.ConfigurationConstants.REPACKAGE_CLASSES_OPTION
import proguard.ConfigurationParser
import proguard.ProGuard
import java.io.File

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

        val tempDir = File.createTempFile("importClasses", null).apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

        val mappingFile = repackageName.orNull?.let { repackage ->
            File(tempDir, "proguard-mapping.txt").apply {
                deleteOnExit()
                writeText(buildString {
                    keepsAndRenames.get().forEach { (originalName, newName) ->
                        if (newName.isNotBlank()) {
                            appendLine("$originalName -> $newName:")
                        }
                    }
                })
            }
        }

        val filesFilter = filters.get().takeUnless { it.isEmpty() }?.joinToString(
            prefix = "(",
            separator = ",",
            postfix = ")",
        ) ?: ""

        val proguardJar = File(tempDir, inputJar.nameWithoutExtension + "-proguarded.jar")

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
                    add(ADAPT_CLASS_STRINGS_OPTION)
                    add(ADAPT_RESOURCE_FILE_NAMES_OPTION)
                    add(ADAPT_RESOURCE_FILE_CONTENTS_OPTION)
                    add(APPLY_MAPPING_OPTION)
                    add(mappingFile!!.absolutePath)
                }
                keepsAndRenames.get().keys.forEach {
                    add(KEEP_OPTION)
                    add(CLASS_KEYWORD)
                    add(it)
                    add("{ public *; }")
                }
                add(INJARS_OPTION)
                add(inputJar.absolutePath)
                inputArtifactDependencies.forEach {
                    add(if (includeTransitiveDependencies.get()) INJARS_OPTION else LIBRARYJARS_OPTION)
                    add(it.absolutePath)
                }
                add(OUTJARS_OPTION)
                add("$proguardJar$filesFilter")
                addAll(extraOptions.get())
            }

            val config = Configuration()
            ConfigurationParser(args.toTypedArray(), null).parse(config)
            ProGuard(config).execute()

            if (proguardJar.exists()) {
                proguardJar.copyTo(outputs.file(inputJar.nameWithoutExtension + "-imported.jar"))
            }

        } finally {
            tempDir.deleteRecursively()
        }
    }

    interface Params : TransformParameters {

        @get:Input
        val keepsAndRenames: MapProperty<String, String>

        @get:Input
        @get:Optional
        val repackageName: Property<String>

        @get:Input
        val filters: ListProperty<String>

        @get:Input
        val extraOptions: ListProperty<String>

        @get:Input
        val includeTransitiveDependencies: Property<Boolean>

    }

}
