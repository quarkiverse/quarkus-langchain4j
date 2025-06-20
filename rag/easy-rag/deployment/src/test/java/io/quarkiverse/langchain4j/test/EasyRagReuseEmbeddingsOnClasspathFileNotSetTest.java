package io.quarkiverse.langchain4j.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.easyrag.runtime.EasyRagConfig.PathType;
import io.quarkus.test.QuarkusUnitTest;

class EasyRagReuseEmbeddingsOnClasspathFileNotSetTest extends EasyRagReuseEmbeddingsDontAlreadyExistBaseTest {
    private static final Path EMBEDDINGS_FILE = Path.of(".", "easy-rag-embeddings.json");

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(Path.of("target", "test-classes", "ragdocuments").toFile(), "ragdocuments")
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.easy-rag.path=ragdocuments
                            quarkus.langchain4j.easy-rag.path-type=CLASSPATH
                            quarkus.langchain4j.easy-rag.reuse-embeddings.enabled=true
                            """),
                            "application.properties"))
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(logRecords -> verifyLogRecords(logRecords, PathType.CLASSPATH));

    @AfterAll
    static void cleanup() throws IOException {
        Files.deleteIfExists(EMBEDDINGS_FILE);
    }

    @Override
    protected Path getEmbeddingsFile() {
        return EMBEDDINGS_FILE;
    }
}
