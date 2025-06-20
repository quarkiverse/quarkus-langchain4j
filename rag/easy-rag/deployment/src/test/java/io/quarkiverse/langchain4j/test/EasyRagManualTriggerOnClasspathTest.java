package io.quarkiverse.langchain4j.test;

import java.nio.file.Path;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.easyrag.EasyRagManualIngestion;
import io.quarkus.test.QuarkusUnitTest;

public class EasyRagManualTriggerOnClasspathTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(Path.of("target", "test-classes", "ragdocuments").toFile(), "ragdocuments")
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.easy-rag.path=ragdocuments
                            quarkus.langchain4j.easy-rag.path-type=CLASSPATH
                            quarkus.langchain4j.easy-rag.ingestion-strategy=MANUAL
                            """),
                            "application.properties"));

    @Inject
    EasyRagManualIngestion trigger;

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    EmbeddingModel embeddingModel;

    @Test
    public void verifyManualIngestion() {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("When was Charlie born?").content())
                .build();
        Assertions.assertTrue(embeddingStore.search(request).matches().isEmpty(),
                "The embedding store has to be empty before " +
                        "the ingestion is triggered");
        trigger.ingest();
        Assertions.assertFalse(embeddingStore.search(request).matches().isEmpty(),
                "The embedding store seems to be empty even after " +
                        "manually triggering ingestion");
    }

}
