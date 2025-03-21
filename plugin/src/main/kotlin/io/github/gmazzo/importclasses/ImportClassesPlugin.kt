package io.github.gmazzo.importclasses

import io.github.gmazzo.importclasses.BuildConfig.PROGUARD_DEFAULT_DEPENDENCY
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.LibraryElements.CLASSES
import org.gradle.api.attributes.LibraryElements.JAR
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.LibraryElements.RESOURCES
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.registerTransform

class ImportClassesPlugin @Inject constructor(
    private val javaToolchains: JavaToolchainService,
) : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {

        val extension =
            extensions.create(ImportClassesExtension::class, EXTENSION_NAME, ImportClassesExtensionImpl::class)

        val proguardConfig = createConfiguration("importClassesProguard")
            .defaultDependencies { add(dependencies.create(PROGUARD_DEFAULT_DEPENDENCY)) }

        extension.proguardMainClass
            .convention("proguard.ProGuard")
            .finalizeValueOnRead()

        extension.proguardJvmArgs
            .finalizeValueOnRead()

        extension.specs.all spec@{

            val disambiguator = this@spec.name

            val suffix = when (disambiguator) {
                MAIN_SOURCE_SET_NAME -> ""
                else -> disambiguator.replaceFirstChar { it.uppercase() }
            }

            sourceSet
                .convention(provider {
                    @Suppress("KotlinConstantConditions")
                    extensions
                        .findByType<SourceSetContainer>()
                        ?.findByName(MAIN_SOURCE_SET_NAME)
                        ?: "sourceSet was not set for $EXTENSION_NAME '$name'. Check https://github.com/gmazzo/gradle-import-classes-plugin#usage for further instructions".let { message ->
                            if (!isGradleSync) error(message) else logger.warn(message)
                            return@let null
                        }
                })
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

            val importsConfig = createConfiguration("importClasses$suffix")

            val librariesConfig = createConfiguration("importClasses${suffix}Libraries")
            librariesConfig.dependencies.addLater(jdkToolchain(javaRuntimeLanguageVersion))

            val elementsDiscriminator = "imported-${disambiguator}"
            val jarElements: LibraryElements = objects.named("$JAR+$elementsDiscriminator")
            val classesElements: LibraryElements = objects.named("$CLASSES+$elementsDiscriminator")
            val resourcesElements: LibraryElements = objects.named("$RESOURCES+$elementsDiscriminator")

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

            dependencies.registerTransform(ExtractJARTransform::class) {
                from.attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, jarElements)
                to.attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, classesElements)
                parameters.forResources = false
            }

            dependencies.registerTransform(ExtractJARTransform::class) {
                from.attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, jarElements)
                to.attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, resourcesElements)
                parameters.forResources = true
            }

            fun extractedFiles(elements: LibraryElements) = importsConfig.incoming
                .artifactView { attributes.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, elements) }
                .files

            // a task is required when dependencies are generated by tasks of the build, since it's exposed as an outgoing variant artifact
            val extractClasses =
                tasks.register<Sync>(importsConfig.name) {
                    from(extractedFiles(classesElements))
                    into(layout.buildDirectory.dir("imported-classes/$disambiguator"))
                    duplicatesStrategy = DuplicatesStrategy.WARN
                }

            afterEvaluate {
                val sourceSet = sourceSet.orNull ?: return@afterEvaluate

                dependencies.add(sourceSet.compileOnlyConfigurationName, extractedFiles(jarElements))
                (sourceSet.output.classesDirs as ConfigurableFileCollection).from(extractClasses)
                sourceSet.resources.srcDir(extractedFiles(resourcesElements))
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

    companion object {
        const val EXTENSION_NAME = "importClasses"
    }

}
