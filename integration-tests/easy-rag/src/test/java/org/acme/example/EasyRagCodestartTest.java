package org.acme.example;

import static io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog.Language.JAVA;
import static io.quarkus.devtools.testing.SnapshotTesting.checkContains;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;

public class EasyRagCodestartTest {

    @RegisterExtension
    public static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
            .setupStandaloneExtensionTest("io.quarkiverse.langchain4j:quarkus-langchain4j-easy-rag")
            .languages(JAVA)
            .build();

    @Test
    void testContent() throws Throwable {
        codestartTest.assertThatGeneratedTreeMatchSnapshots(JAVA, "easy-rag-catalog");
        codestartTest.checkGeneratedSource(JAVA, "org.acme.Bot")
                .satisfies(checkContains("interface Bot"));
    }

}
