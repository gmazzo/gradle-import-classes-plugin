package io.github.gmazzo.importclasses

import com.android.tools.r8.R8
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

        val rulesFile = File(tempDir, "proguard-rules.txt").apply {
            deleteOnExit()
            writeText(buildString {
                appendLine("-forceprocessing")
                appendLine("-dontwarn")
                appendLine("-dontoptimize")
                repackageName.orNull?.let {
                    appendLine("-repackageclasses $it")
                    appendLine("-adaptresourcefilenames")
                    appendLine("-applymapping ${mappingFile!!.absolutePath}")
                }
                keeps.get().forEach {
                    appendLine("-keep,allowobfuscation class $it { *; }")
                }
                appendLine("-injars ${inputJar.absolutePath}$filesFilter")
                inputArtifactDependencies.forEach {
                    appendLine("-injars ${it.absolutePath}$filesFilter")
                }
            })
        }

        try {
            val args = buildList {
                add("--classfile")
                add("--output")
                add(outputs.dir(inputJar.nameWithoutExtension + "-extracted").absolutePath)
                add("--pg-conf")
                add(rulesFile.absolutePath)
            }

            R8.main(args.toTypedArray())

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
