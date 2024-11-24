package io.quarkiverse.langchain4j.chatbot.it;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog.Language;
import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;
import io.quarkus.maven.dependency.ArtifactCoords;

public class Langchain4jChatbotCodestartTest {

    @RegisterExtension
    public static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
            .languages(Language.JAVA)
            .extensions(
                    List.of(ArtifactCoords.fromString("io.quarkiverse.langchain4j:quarkus-langchain4j-openai:999-SNAPSHOT")))
            .setupStandaloneExtensionTest("io.quarkiverse.langchain4j:quarkus-langchain4j-chatbot")
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
