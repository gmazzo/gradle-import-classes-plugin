package io.github.gmazzo.importclasses

import org.gradle.api.Named
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.toolchain.JavaLanguageVersion

@JvmDefaultWithoutCompatibility
interface ImportClassesSpec : Named {

    /**
     * The target [SourceSet] to import the classes into
     */
    val sourceSet: Property<SourceSet>

    /**
     * Sets the target [SourceSet] to import the classes into
     */
    fun sourceSet(sourceSet: SourceSet) = apply {
        this.sourceSet.value(sourceSet)
    }

    /**
     * Optional. The target package to repackage the classes to.
     */
    val repackageTo: Property<String>

    /**
     * Sets the target package to repackage the classes to.
     */
    fun repackageTo(repackageTo: String) = apply {
        this.repackageTo.value(repackageTo)
    }

    /**
     * The `-keep` rules to apply to the classes.
     * If the value is not blank, the class will be renamed to the given class name.
     */
    val keepsAndRenames: MapProperty<String, String>

    /**
     * Targets the given [className] for being imported.
     */
    fun keep(className: String) =
        keep(className, null)

    /**
     * Targets the given [className] for being imported and renamed to the given [renameTo] (`null` for keep the original name).
     */
    fun keep(className: String, renameTo: String?) = apply {
        if (renameTo != null) {
            keepsAndRenames.put(className, renameTo)

        } else {
            keepsAndRenames.put(
                className, repackageTo
                    .map { repackage -> "${repackage}.${className.substring(className.lastIndexOf('.') + 1)}" }
                    .orElse("")
            )
        }
    }

    /**
     * Controls what classes and resources gets imported into the target `SourceSet`.
     *
     * The patterns are matched against the relative path of the class file inside the jar, and order matters.
     *
     * There is no default, but a good standard should be:
     * ```kotlin
     * exclude("META-INF/**/module-info.class")
     *include("**.class")
     *exclude("**.*")
     * ```
     */
    val filters: SetProperty<String>

    /**
     * Adds a positive pattern to the [filters].
     *
     * @see filters
     */
    fun include(vararg pattern: String) = apply {
        filters.addAll(*pattern)
    }

    /**
     * Adds a negative (`!` prefixed) pattern to the [filters].
     *
     * @see filters
     */
    fun exclude(vararg pattern: String) = apply {
        filters.addAll(pattern.map { "!$it" })
    }

    /**
     * Allows to add any custom extra rule to `Proguard`'s configuration
     */
    val extraOptions: ListProperty<String>

    /**
     * Adds a custom option (Proguard` rule) to the [extraOptions].
     *
     * @see extraOptions
     */
    fun option(vararg option: String) = apply {
        extraOptions.addAll(*option)
    }

    /**
     * If set, a [org.gradle.jvm.toolchain.JavaToolchainSpec] runtime will be appended to `-libraryjars`.
     *
     * Defaults to `java.toolchain.languageVersion`.
     */
    val javaRuntimeLanguageVersion: Property<JavaLanguageVersion>

}
