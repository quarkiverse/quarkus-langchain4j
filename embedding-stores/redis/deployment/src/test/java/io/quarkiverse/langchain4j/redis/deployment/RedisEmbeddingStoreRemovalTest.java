package io.quarkiverse.langchain4j.redis.deployment;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import dev.langchain4j.store.embedding.filter.Filter;
import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.QuarkusExtensionTest;

public class RedisEmbeddingStoreRemovalTest extends EmbeddingStoreWithRemovalIT {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.redis.dimension=384\n" +
                            "quarkus.langchain4j.redis.numeric-metadata-fields=num\n"),
                            "application.properties"));

    @Inject
    RedisEmbeddingStore embeddingStore;

    @Inject
    RedisDataSource redis;

    private static EmbeddingModel embeddingModel;

    @BeforeAll
    public static void initEmbeddingModel() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @BeforeEach
    void clearStore() {
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

    // the inherited test filters on a textual field, which RedisFilterMapper does not support yet
    @Override
    protected boolean supportsRemoveAllByFilter() {
        return false;
    }

    @Test
    void should_remove_all_by_numeric_filter() {
        Embedding embedding = embeddingModel.embed("hello").content();
        String id1 = embeddingStore.add(embedding, TextSegment.from("a", new Metadata().put("num", 1)));
        embeddingStore.add(embedding, TextSegment.from("b", new Metadata().put("num", 2)));
        embeddingStore.add(embedding, TextSegment.from("c", new Metadata().put("num", 3)));
        String id4 = embeddingStore.add(embedding);

        embeddingStore.removeAll(metadataKey("num").isGreaterThanOrEqualTo(2));

        assertThat(getAllEmbeddings()).extracting(EmbeddingMatch::embeddingId).containsExactlyInAnyOrder(id1, id4);
    }

    @Test
    void should_fail_to_remove_all_by_null_filter() {
        assertThatThrownBy(() -> embeddingStore.removeAll((Filter) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("filter cannot be null");
    }

    @Test
    void should_remove_more_keys_than_one_batch() {
        Embedding embedding = embeddingModel.embed("hello").content();
        int count = 2500;
        int chunk = 500;
        for (int start = 0; start < count; start += chunk) {
            List<String> ids = IntStream.range(start, start + chunk).mapToObj(i -> "id-" + i).toList();
            List<TextSegment> segments = IntStream.range(start, start + chunk)
                    .mapToObj(i -> TextSegment.from("segment " + i, new Metadata().put("num", i % 2)))
                    .toList();
            embeddingStore.addAll(ids, Collections.nCopies(chunk, embedding), segments);
        }
        assertThat(countKeys()).isEqualTo(count);

        embeddingStore.removeAll(metadataKey("num").isEqualTo(0));
        assertThat(countKeys()).isEqualTo(count / 2);

        embeddingStore.removeAll();
        assertThat(countKeys()).isZero();
    }

    private long countKeys() {
        return redis.key().keys("embedding:*").size();
    }
}
