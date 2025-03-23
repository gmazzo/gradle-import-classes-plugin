package io.github.gmazzo.importclasses

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Component
import com.android.build.api.variant.ScopedArtifacts
import io.github.gmazzo.importclasses.BuildConfig.PROGUARD_DEFAULT_DEPENDENCY
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.LibraryElements.JAR
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.kotlin.dsl.the
import org.jetbrains.annotations.VisibleForTesting

class ImportClassesPlugin @Inject constructor(
    private val javaToolchains: JavaToolchainService,
) : Plugin<Project> {

    override fun apply(project: Project): Unit = with(project) {

        val extension =
            extensions.create(
                ImportClassesExtension::class,
                EXTENSION_NAME,
                ImportClassesExtensionImpl::class
            ) as ImportClassesExtensionImpl

        val proguardConfig = createConfiguration("importClassesProguard")
            .defaultDependencies { add(dependencies.create(PROGUARD_DEFAULT_DEPENDENCY)) }

        extension.proguardMainClass
            .convention("proguard.ProGuard")
            .finalizeValueOnRead()

        extension.proguardJvmArgs
            .finalizeValueOnRead()

        extension.specs.all spec@{

            disambiguator = this@spec.name

            val suffix = when (disambiguator) {
                SourceSet.MAIN_SOURCE_SET_NAME -> ""
                else -> disambiguator.replaceFirstChar { it.uppercase() }
            }

            intoSourceSet
                .convention(name)
                .finalizeValueOnRead()

            keepsAndRenames
                .finalizeValueOnRead()

            repackageTo
                .finalizeValueOnRead()

            filters
                .finalizeValueOnRead()

            extraOptions
                .finalizeValueOnRead()

            javaRuntimeLanguageVersion
                .convention(provider { extensions.findByType<JavaPluginExtension>() }
                    .flatMap { it.toolchain.languageVersion })
                .finalizeValueOnRead()

            // excludes by default all known resources related to the module build process
            exclude(
                "META-INF/LICENSE.txt",
                "META-INF/MANIFEST.MF",
                "META-INF/*.kotlin_module",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/maven/**",
                "META-INF/versions/*/module-info.class"
            )

            importsConfig = createConfiguration("importClasses$suffix")
            librariesConfig = createConfiguration("importClasses${suffix}Libraries")
            librariesConfig.dependencies.addLater(jdkToolchain(javaRuntimeLanguageVersion))

            val elementsDiscriminator = "imported-${disambiguator}"
            val jarElements: LibraryElements = objects.named("$JAR+$elementsDiscriminator")

            val librariesModuleIds by lazy {
                librariesConfig.incoming.artifacts.mapTo(mutableSetOf()) { it.id.componentIdentifier.comparableId }
            }

            val importJars = importsConfig.incoming
                .artifactView { componentFilter { it.comparableId !in librariesModuleIds } }
                .files

            dependencies.registerTransform(ImportClassesTransform::class) {
                from.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(JAR))
                to.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, jarElements)
                parameters.workDirectory.value(layout.buildDirectory.dir("tmp/${importsConfig.name}"))
                parameters.proguardClasspath.from(proguardConfig)
                parameters.proguardMainClass.value(extension.proguardMainClass)
                parameters.proguardJvmArgs.value(extension.proguardJvmArgs)
                parameters.importClasspath.from(importJars)
                parameters.librariesClasspath.from(librariesConfig)
                parameters.keepsAndRenames.value(keepsAndRenames.map {
                    check(it.isNotEmpty()) { "Must call `keep(<classname>)` at least once" }; it
                })
                parameters.repackageName.value(repackageTo)
                parameters.filters.value(filters)
                parameters.extraOptions.value(extraOptions)
            }

            extractedJars = importsConfig.incoming
                .artifactView { attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, jarElements) }
                .files

        }

        plugins.withId("com.android.base") {
            with(AndroidSupport) { bindSpecs(extension) }
        }
        afterEvaluate {
            plugins.withId("java") {
                sourceSets.all ss@{
                    for (spec in extension.specsByTarget.get()[this@ss.name].orEmpty()) {
                        bindToSpec(spec, project)
                    }
                }
            }

            val validateSpecsAreBound: Boolean? by project
            if (validateSpecsAreBound != false) {
                validateSpecsAreBound(extension)
            }
        }
    }

    @VisibleForTesting
    internal fun Project.validateSpecsAreBound(
        extension: ImportClassesExtensionImpl = extensions.getByName<ImportClassesExtensionImpl>("importClasses"),
    ) {
        extension.specs.all spec@{
            if (!bound) {
                val errorMsg =
                    "Target sourceSet was not set for $EXTENSION_NAME '${this@spec.name}'. Check https://github.com/gmazzo/gradle-import-classes-plugin#usage for further instructions"

                if (isGradleSync) logger.warn(errorMsg) else error(errorMsg)
            }
        }
    }

    private val isGradleSync
        get() = System.getProperty("idea.sync.active") == "true"

    private fun Project.createConfiguration(name: String) = configurations.create(name) {
        isCanBeResolved = true
        isCanBeConsumed = false
        attributes {
            attribute(USAGE_ATTRIBUTE, objects.named(JAVA_RUNTIME))
            attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
            attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(JAR))
        }
    }

    private fun Project.jdkToolchain(version: Provider<JavaLanguageVersion>) = version
        .flatMap { javaToolchains.launcherFor { languageVersion.set(it) } }
        .map { it.metadata.installationPath }
        .map { fileTree(it).include("**/*.jar", "**/*.jmod") }
        .map(dependencies::create)

    private val ComponentIdentifier.comparableId
        get() = when (this) {
            is ModuleComponentIdentifier -> moduleIdentifier
            else -> this
        }

    private fun SourceSet.bindToSpec(spec: ImportClassesSpecImpl, project: Project) {
        spec.bound = true

        val extractTask = project.tasks.register<ImportClassesTask>(spec.importsConfig.name) {
            sources.from(spec.extractedJars)
            extractedClassesDir.set(project.layout.buildDirectory.dir("imported/${spec.disambiguator}/classes"))
            extractedResourcesDir.set(project.layout.buildDirectory.dir("imported/${spec.disambiguator}/resources"))
        }

        project.dependencies.add(compileOnlyConfigurationName, spec.extractedJars)
        (output.classesDirs as ConfigurableFileCollection).from(extractTask.map { it.extractedClassesDir })
        resources.srcDir(extractTask.map { it.extractedResourcesDir })
    }

    private object AndroidSupport {

        fun Project.bindSpecs(extension: ImportClassesExtensionImpl) {
            val androidComponents: AndroidComponentsExtension<*, *, *> by extensions

            androidComponents.onVariants(androidComponents.selector().all()) { variant ->
                val suffix = variant.name.replaceFirstChar { it.uppercase() }
                val importTask = project.tasks.register<ImportClassesTask>("importClassesFor$suffix")

                val specs = sequenceOf(extension) + variant.allNames.mapNotNull(extension.specsByTarget.get()::get).flatten().toSet()
                for (spec in specs) {
                    variant.bindToSpec(spec, dependencies, importTask)
                }
            }
        }

        private val Component.allNames
            get() = listOfNotNull(name, buildType, flavorName)

        private fun Component.bindToSpec(
            spec: ImportClassesSpecImpl,
            dependencies: DependencyHandler,
            importTask: TaskProvider<ImportClassesTask>,
        ) {
            spec.bound = true

            importTask.configure { sources.from(spec.extractedJars) }

            compileConfiguration.dependencies.add(dependencies.create(spec.extractedJars))
            artifacts.forScope(ScopedArtifacts.Scope.PROJECT).apply {
                use(importTask).toAppend(ScopedArtifact.CLASSES, ImportClassesTask::extractedClassesDir)
                use(importTask).toAppend(ScopedArtifact.JAVA_RES, ImportClassesTask::extractedResourcesDir)
            }
        }

    }

    companion object {
        const val EXTENSION_NAME = "importClasses"

        private val Project.sourceSets
            get() = the<SourceSetContainer>()
    }

}
