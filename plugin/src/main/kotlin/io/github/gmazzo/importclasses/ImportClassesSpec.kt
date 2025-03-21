package io.github.gmazzo.importclasses

import org.gradle.api.Named
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.jvm.toolchain.JavaLanguageVersion

interface ImportClassesSpec : Named {

    /**
     * The [org.gradle.api.tasks.SourceSet] (or Android's [com.android.build.api.variant]) to bind the imported classes to.
     *
     * Defaults to this spec's [name].
     */
    val intoSourceSet: Property<String>

    /**
     * The dependencies from where to import classes from.
     *
     * For instance:
     * ```kotlin
     * importClasses {
     *   dependencies("org.example:library:1.0")
     * }
     * ```.
     *
     * It's a shorthand for project's `dependencies` block:
     * ```kotlin
     * dependencies {
     *   importClasses("org.example:library:1.0")
     * }
     * ```
     */
    fun dependencies(vararg dependencies: Any): ImportClassesSpec

    /**
     * Extra dependencies to be mapped to Proguard's [`-libraryjars`](https://www.guardsquare.com/manual/configuration/usage#libraryjars) option.
     *
     * For instance:
     * ```kotlin
     * importClasses {
     *   libraries("org.example:library:1.0")
     * }
     * ```.
     *
     * It's a shorthand for project's `dependencies` block:
     * ```kotlin
     * dependencies {
     *   importClassesLibraries("org.example:library:1.0")
     * }
     * ```
     */
    fun libraries(vararg dependencies: Any): ImportClassesSpec

    /**
     * Optional. The target package to repackage the classes to.
     */
    val repackageTo: Property<String>

    /**
     * Sets the target package to repackage the classes to.
     */
    fun repackageTo(repackageTo: String): ImportClassesSpec

    /**
     * The `-keep` rules to apply to the classes.
     * If the value is not blank, the class will be renamed to the given class name.
     */
    val keepsAndRenames: MapProperty<String, String>

    /**
     * Targets the given [className] for being imported.
     */
    fun keep(className: String): ImportClassesSpec

    /**
     * Targets the given [className] for being imported and renamed to the given [renameTo] (`null` for keep the original name).
     */
    fun keep(className: String, renameTo: String?): ImportClassesSpec

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
    fun include(vararg pattern: String): ImportClassesSpec

    /**
     * Adds a negative (`!` prefixed) pattern to the [filters].
     *
     * @see filters
     */
    fun exclude(vararg pattern: String): ImportClassesSpec

    /**
     * Allows to add any custom extra rule to `Proguard`'s configuration
     */
    val extraOptions: ListProperty<String>

    /**
     * Adds a custom option (Proguard` rule) to the [extraOptions].
     *
     * @see extraOptions
     */
    fun option(vararg option: String): ImportClassesSpec

    /**
     * If set, a [org.gradle.jvm.toolchain.JavaToolchainSpec] runtime will be appended to `-libraryjars`.
     *
     * Defaults to `java.toolchain.languageVersion`.
     */
    val javaRuntimeLanguageVersion: Property<JavaLanguageVersion>

}
