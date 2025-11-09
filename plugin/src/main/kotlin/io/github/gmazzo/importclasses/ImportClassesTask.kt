package io.github.gmazzo.importclasses

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "This task is not cacheable")
public abstract class ImportClassesTask @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val fileSystemOperations: FileSystemOperations,
) : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    public abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    public abstract val extractedClassesDir: DirectoryProperty

    @get:OutputDirectory
    public abstract val extractedResourcesDir: DirectoryProperty

    @TaskAction
    public fun copyClasses() {
        val sourcesContent = sources.asFileTree.map(archiveOperations::zipTree)

        fileSystemOperations.sync {
            duplicatesStrategy = DuplicatesStrategy.WARN
            from(sourcesContent)
            include("**/*.class")
            into(extractedClassesDir)
        }
        fileSystemOperations.sync {
            duplicatesStrategy = DuplicatesStrategy.WARN
            from(sourcesContent)
            exclude("**/*.class")
            into(extractedResourcesDir)
        }
    }

}
