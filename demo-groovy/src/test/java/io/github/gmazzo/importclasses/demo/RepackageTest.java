package io.github.gmazzo.importclasses.demo;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepackageTest {

    @ParameterizedTest
    @CsvSource({
            "org.eclipse.jgit.api.Git, false",
            "org.eclipse.jgit.ignore.FastIgnoreRule, false",
            "io.github.gmazzo.importclasses.demo.imported.FastIgnoreRule, true",
    })
    void classesShouldBeShrinkAndRepackaged(String className, boolean expectedFound) {
        boolean exists = false;
        try {
            Class.forName(className);
            exists = true;
        } catch (ClassNotFoundException ignored) {
        }

        assertEquals(expectedFound, exists);
    }

}
