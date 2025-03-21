package io.github.gmazzo.importclasses.demo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class RepackageTest {

    @ParameterizedTest
    @CsvSource(
        "org.eclipse.jgit.api.Git, false",
        "org.apache.commons.lang3.StringUtils, false",
        "io.github.gmazzo.importclasses.demo.imported.StringUtils, true",
    )
    fun `classes should be shrink and repackaged`(className: String, expectedFound: Boolean) {
        val exists = runCatching { Class.forName(className) }.isSuccess

        assertEquals(expectedFound, exists)
    }

}
