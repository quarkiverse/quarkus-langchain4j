package io.quarkiverse.langchain4j.redis.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

public class RedisEmbeddingStoreTest extends EmbeddingStoreIT {

    // if a metadata field is a number, create a field of type NUMERIC in the Redis index
    static String numericMetadataFields = new RedisEmbeddingStoreTest().createMetadata().toMap().entrySet()
            .stream()
            .filter(e -> e.getValue() instanceof Number)
            .map(Map.Entry::getKey)
            .collect(Collectors.joining(","));

    // if a metadata field is not a number, treat it as a string and create a field of type TEXT for it
    static String textualMetadataFields = new RedisEmbeddingStoreTest().createMetadata().toMap().entrySet()
            .stream()
            .filter(e -> !(e.getValue() instanceof Number))
            .map(Map.Entry::getKey)
            .collect(Collectors.joining(","));

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.redis.dimension=384\n" +
                            "quarkus.langchain4j.redis.numeric-metadata-fields=" + numericMetadataFields + "\n" +
                            "quarkus.langchain4j.redis.textual-metadata-fields=" + textualMetadataFields + "\n"),
                            "application.properties"));

    @Inject
    RedisEmbeddingStore embeddingStore;

    private static EmbeddingModel embeddingModel;

    /**
     * FIXME: This is a workaround to avoid loading the embedding model in this test class' static initializer,
     * because otherwise we hit
     * java.lang.UnsatisfiedLinkError: Native Library (/path/to/the/library) already loaded in another classloader
     * because the test class is loaded by JUnit and by Quarkus in different class loaders.
     */
    @BeforeAll
    public static void initEmbeddingModel() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Test
    void should_return_more_than_ten_results() {
        List<Embedding> embeddings = IntStream.range(0, 15)
                .mapToObj(i -> embeddingModel.embed("text " + i).content())
                .toList();
        embeddingStore.addAll(embeddings);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddings.get(0))
                .maxResults(15)
                .build();

        assertThat(embeddingStore.search(request).matches()).hasSize(15);
    }
}
