package io.github.gmazzo.importclasses

import org.gradle.api.tasks.SourceSetContainer
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.the

class ImportClassesPluginTest {

    @CsvSource(
        ",",
        "java",
        "java-library",
        "groovy",
        "kotlin",
    )
    @ParameterizedTest
    fun `plugin can be applied`(plugin: String?): Unit = with(ProjectBuilder.builder().build()) {
        apply(plugin = "io.github.gmazzo.importclasses")
        if (plugin != null) { apply(plugin = plugin) }

        the<SourceSetContainer>().maybeCreate("main").apply {
            the<ImportClassesExtension>()("org.apache.commons:commons-lang3:3.14.0") {
                repackageTo.value("org.test.imported")
                keep("org.apache.commons.lang3.StringUtils")
            }
        }
    }

}
