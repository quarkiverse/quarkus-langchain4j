package org.acme.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog.Language;
import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;

public class Langchain4jCodeStart {

    @RegisterExtension
    public static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
            .languages(Language.JAVA)
            .setupStandaloneExtensionTest("org.acme:greeting-extension")
            .build();

    /**
     * Make sure the generated code meets the expectations.
     * <br>
     * The generated code uses mocked data to be immutable and allow snapshot testing.
     * <br>
     * <br>
     *
     * Read the doc: <br>
     *
     */
    @Test
    void testContent() throws Throwable {
        //codestartTest.checkGeneratedSource("org.acme.SomeClass");
        //codestartTest.assertThatGeneratedFileMatchSnapshot(Language.JAVA, "\"src/main/resources/some-resource.ext");
    }

    /**
     * This test runs the build (with tests) on generated projects for all selected languages
     */
    @Test
    void buildAllProjects() throws Throwable {
        codestartTest.buildAllProjects();
    }
}
