package io.github.gmazzo.importclasses

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

interface ImportClassesSpec {

    /**
     * Optional. The target package to repackage the classes to.
     */
    val repackageTo: Property<String>

    /**
     * The `-keep` rules to apply to the classes.
     * If the value is not blank, the class will be renamed to the given class name.
     */
    val keepsAndRenames: MapProperty<String, String>

    /**
     * Targets the given [className] for being imported.
     */
    fun keep(className: String)

    /**
     * Targets the given [className] for being imported and renamed to the given [renameTo] (`null` for keep the original name).
     */
    fun keep(className: String, renameTo: String?)

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
    fun include(vararg pattern: String)

    /**
     * Adds a negative (`!` prefixed) pattern to the [filters].
     *
     * @see filters
     */
    fun exclude(vararg pattern: String)

    /**
     * Allows to add any custom extra rule to `Proguard`'s configuration
     */
    val extraOptions: ListProperty<String>

    /**
     * Adds a custom option (Proguard` rule) to the [extraOptions].
     *
     * @see extraOptions
     */
    fun option(vararg option: String)

    /**
     * By default, [keepsAndRenames] will only look for classes in the direct dependencies
     * (any transitive dependency will be mapped to Proguard as `-libraryjars`).
     *
     * Enable this to include transitive dependencies as well, and map them as `-injars`.
     * This will affect the outcome of the final class
     */
    val includeTransitiveDependencies: Property<Boolean>

}
