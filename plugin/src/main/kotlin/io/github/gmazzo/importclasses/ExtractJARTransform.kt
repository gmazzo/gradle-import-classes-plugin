package io.github.gmazzo.importclasses

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.zip.ZipInputStream

@DisableCachingByDefault(because = "Not worth caching")
abstract class ExtractJARTransform : TransformAction<ExtractJARTransform.Params> {

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs): Unit = with(parameters) {
        val inputJar = inputArtifact.get().asFile
        val outDir by lazy { outputs.dir("${inputJar.nameWithoutExtension}-${if (forResources) "resources" else "classes"}") }

        ZipInputStream(inputJar.inputStream()).use { zip ->
            do {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue

                if (forResources xor entry.name.endsWith(".class")) {
                    File(outDir, entry.name)
                        .apply { parentFile.mkdirs() }
                        .outputStream()
                        .use(zip::copyTo)
                }
            } while (true)
        }
    }

    interface Params : TransformParameters {

        @get:Input
        var forResources: Boolean

    }

}
