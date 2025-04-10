package io.github.gmazzo.importclasses

import java.io.FileOutputStream
import javax.inject.Inject
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.ExecOperations
import org.gradle.process.internal.ExecException
import proguard.ConfigurationConstants.ADAPT_CLASS_STRINGS_OPTION
import proguard.ConfigurationConstants.ADAPT_RESOURCE_FILE_CONTENTS_OPTION
import proguard.ConfigurationConstants.ADAPT_RESOURCE_FILE_NAMES_OPTION
import proguard.ConfigurationConstants.APPLY_MAPPING_OPTION
import proguard.ConfigurationConstants.CLASS_KEYWORD
import proguard.ConfigurationConstants.DONT_OPTIMIZE_OPTION
import proguard.ConfigurationConstants.DONT_USE_MIXED_CASE_CLASS_NAMES_OPTION
import proguard.ConfigurationConstants.FORCE_PROCESSING_OPTION
import proguard.ConfigurationConstants.IGNORE_WARNINGS_OPTION
import proguard.ConfigurationConstants.INJARS_OPTION
import proguard.ConfigurationConstants.KEEP_ATTRIBUTES_OPTION
import proguard.ConfigurationConstants.KEEP_OPTION
import proguard.ConfigurationConstants.LIBRARYJARS_OPTION
import proguard.ConfigurationConstants.OUTJARS_OPTION
import proguard.ConfigurationConstants.PRINT_CONFIGURATION_OPTION
import proguard.ConfigurationConstants.PRINT_MAPPING_OPTION
import proguard.ConfigurationConstants.PRINT_SEEDS_OPTION
import proguard.ConfigurationConstants.PRINT_USAGE_OPTION
import proguard.ConfigurationConstants.REPACKAGE_CLASSES_OPTION
import proguard.ConfigurationConstants.USE_UNIQUE_CLASS_MEMBER_NAMES_OPTION

@CacheableTransform
abstract class ImportClassesTransform @Inject constructor(
    private val execOperations: ExecOperations,
) : TransformAction<ImportClassesTransform.Params> {

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs): Unit = with(parameters) {
        if (inputArtifact.get().asFile != importClasspath.firstOrNull()) {
            // since transform will run per each dependency in the graph,
            // we only run it once for the first main dependency
            return@with
        }

        val tempDir = workDirectory.asFile.get().apply {
            deleteRecursively()
            mkdirs()
        }

        val mappingFile = repackageName.orNull?.let { repackage ->
            tempDir.resolve("keeps.txt").apply {
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

        val libraries = librariesClasspath.asFileTree.files
        val proguardJar = tempDir.resolve(inputArtifact.get().asFile.nameWithoutExtension + "-imported.jar")

        val proguardArgs = buildList {
            add(PRINT_SEEDS_OPTION); add("seeds.txt")
            add(PRINT_USAGE_OPTION); add("usage.txt")
            add(PRINT_MAPPING_OPTION); add("mappings.txt")
            add(PRINT_CONFIGURATION_OPTION); add("configuration.txt")
            add(FORCE_PROCESSING_OPTION)
            add(IGNORE_WARNINGS_OPTION)
            add(DONT_OPTIMIZE_OPTION)
            add(DONT_USE_MIXED_CASE_CLASS_NAMES_OPTION)
            add(USE_UNIQUE_CLASS_MEMBER_NAMES_OPTION)
            add(KEEP_ATTRIBUTES_OPTION); add("*")
            repackageName.orNull?.let {
                add(REPACKAGE_CLASSES_OPTION); add(it)
                add(ADAPT_CLASS_STRINGS_OPTION)
                add(ADAPT_RESOURCE_FILE_NAMES_OPTION)
                add(ADAPT_RESOURCE_FILE_CONTENTS_OPTION)
                add(APPLY_MAPPING_OPTION); add(mappingFile!!.name)
            }
            keepsAndRenames.get().keys.forEach {
                add(KEEP_OPTION); add(CLASS_KEYWORD); add(it); add("{ public *; }")
            }
            importClasspath.asFileTree.forEach {
                if (it !in libraries) {
                    add(INJARS_OPTION); add(it.absolutePath)
                }
            }
            librariesClasspath.asFileTree.forEach {
                add(LIBRARYJARS_OPTION); add(it.absolutePath)
            }
            add(OUTJARS_OPTION); add("$proguardJar$filesFilter")
            addAll(extraOptions.get())
        }

        val stdOutFile = tempDir.resolve("output.txt")
        val stdOutStream = FileOutputStream(stdOutFile)

        try {
            execOperations.javaexec {
                workingDir = tempDir
                mainClass.value(proguardMainClass.orNull)
                classpath = proguardClasspath
                jvmArgs = proguardJvmArgs.get()
                args = proguardArgs
                standardOutput = stdOutStream
                errorOutput = stdOutStream
            }.rethrowFailure()

        } catch (e: Exception) {
            throw ExecException("Proguard failed, check $stdOutFile for details", e)
        }

        // validates all keeps are found
        val seedsFile = tempDir.resolve("seeds.txt")
        if (seedsFile.exists()) {
            val seeds = seedsFile.useLines { lines -> lines.mapTo(mutableSetOf()) { it.replace(":.*$", "") } }
            val missingSeeds = keepsAndRenames.keySet().get() - seeds

            if (missingSeeds.isNotEmpty()) {
                error(missingSeeds.joinToString(prefix = "Classes were not found: ", separator = ", "))
            }
        }

        if (proguardJar.exists()) {
            proguardJar.copyTo(outputs.file(proguardJar.name))
        }
    }

    interface Params : TransformParameters {

        @get:Internal
        val workDirectory: DirectoryProperty

        @get:Classpath
        val proguardClasspath: ConfigurableFileCollection

        @get:Input
        val proguardMainClass: Property<String>

        @get:Input
        val proguardJvmArgs: ListProperty<String>

        @get:Classpath
        val importClasspath: ConfigurableFileCollection

        @get:Classpath
        val librariesClasspath: ConfigurableFileCollection

        @get:Input
        val keepsAndRenames: MapProperty<String, String>

        @get:Input
        @get:Optional
        val repackageName: Property<String>

        @get:Input
        val filters: ListProperty<String>

        @get:Input
        val extraOptions: ListProperty<String>

    }

}
