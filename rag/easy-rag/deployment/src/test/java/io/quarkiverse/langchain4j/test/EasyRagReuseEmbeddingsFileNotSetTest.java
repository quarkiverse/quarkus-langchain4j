package io.quarkiverse.langchain4j.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class EasyRagReuseEmbeddingsFileNotSetTest extends EasyRagReuseEmbeddingsDontAlreadyExistBaseTest {
    private static final Path EMBEDDINGS_FILE = Path.of(".", "easy-rag-embeddings.json");

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.easy-rag.path=src/test/resources/ragdocuments
                            quarkus.langchain4j.easy-rag.reuse-embeddings.enabled=true
                            """),
                            "application.properties"))
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(EasyRagReuseEmbeddingsDontAlreadyExistBaseTest::verifyLogRecords);

    @AfterAll
    static void cleanup() throws IOException {
        Files.deleteIfExists(EMBEDDINGS_FILE);
    }

    @Override
    protected Path getEmbeddingsFile() {
        return EMBEDDINGS_FILE;
    }
}
