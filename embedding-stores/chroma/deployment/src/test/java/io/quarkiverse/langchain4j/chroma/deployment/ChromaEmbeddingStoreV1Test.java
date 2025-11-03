package io.quarkiverse.langchain4j.chroma.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

public class ChromaEmbeddingStoreV1Test extends EmbeddingStoreIT {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            """
                                    # 0.6.2 is the last version with the V1 API that seems to work
                                    # 0.6.3 weirdly doesn't work
                                    quarkus.langchain4j.chroma.devservices.image-name=ghcr.io/chroma-core/chroma:0.6.2
                                    quarkus.langchain4j.chroma.api-version=V1
                                    """),
                            "application.properties"));

    @Inject
    ChromaEmbeddingStore store;

    static EmbeddingModel embeddingModel;

    @BeforeAll
    public static void initEmbeddingModel() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @Override
    protected void clearStore() {
        store.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return store;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
