package io.quarkiverse.langchain4j.test;

import static java.util.Comparator.reverseOrder;

import java.io.File;
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

class EasyRagReuseEmbeddingsOnClasspathDontAlreadyExistTest extends EasyRagReuseEmbeddingsDontAlreadyExistBaseTest {
    private static final String EMBEDDING_FILE_NAME = "embeddings.json";

    // Didn't use @TempDir because it didn't work well with @RegisterExtension
    private static final Path TEMP_DIR = Path.of("target", "test-generated-data",
            EasyRagReuseEmbeddingsOnClasspathDontAlreadyExistTest.class.getSimpleName());

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(Path.of("target", "test-classes", "ragdocuments").toFile(), "ragdocuments")
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.easy-rag.path=ragdocuments
                            quarkus.langchain4j.easy-rag.path-type=CLASSPATH
                            quarkus.langchain4j.easy-rag.reuse-embeddings.enabled=true
                            quarkus.langchain4j.easy-rag.reuse-embeddings.file=%s
                            """.formatted(embeddingsFile())),
                            "application.properties"))
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(logRecords -> verifyLogRecords(logRecords, PathType.CLASSPATH));

    private static Path embeddingsFile() {
        return TEMP_DIR.resolve(EMBEDDING_FILE_NAME).toAbsolutePath();
    }

    @AfterAll
    static void cleanup() throws IOException {
        // Clean up the temp directory
        Files.walk(TEMP_DIR.getParent())
                .sorted(reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @Override
    protected Path getEmbeddingsFile() {
        return embeddingsFile();
    }
}
