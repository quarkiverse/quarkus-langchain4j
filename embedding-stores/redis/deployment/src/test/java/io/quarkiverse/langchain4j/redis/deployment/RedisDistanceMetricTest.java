package io.quarkiverse.langchain4j.redis.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;
import io.quarkus.test.QuarkusExtensionTest;

public class RedisDistanceMetricTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.redis.default-store-enabled=false
                            quarkus.langchain4j.redis.cosine.client-name=<default>
                            quarkus.langchain4j.redis.cosine.dimension=3
                            quarkus.langchain4j.redis.cosine.index-name=cosine-index
                            quarkus.langchain4j.redis.cosine.prefix=cosine:
                            quarkus.langchain4j.redis.cosine.distance-metric=COSINE
                            quarkus.langchain4j.redis.ip.client-name=<default>
                            quarkus.langchain4j.redis.ip.dimension=3
                            quarkus.langchain4j.redis.ip.index-name=ip-index
                            quarkus.langchain4j.redis.ip.prefix=ip:
                            quarkus.langchain4j.redis.ip.distance-metric=IP
                            quarkus.langchain4j.redis.l2.client-name=<default>
                            quarkus.langchain4j.redis.l2.dimension=3
                            quarkus.langchain4j.redis.l2.index-name=l2-index
                            quarkus.langchain4j.redis.l2.prefix=l2:
                            quarkus.langchain4j.redis.l2.distance-metric=L2
                            """),
                            "application.properties"));

    private static final Embedding QUERY = Embedding.from(new float[] { 1, 0, 0 });
    private static final Embedding SAME = Embedding.from(new float[] { 1, 0, 0 });
    private static final Embedding ORTHOGONAL = Embedding.from(new float[] { 0, 1, 0 });
    private static final Embedding OPPOSITE = Embedding.from(new float[] { -1, 0, 0 });

    @Inject
    @EmbeddingStoreName("cosine")
    RedisEmbeddingStore cosineStore;

    @Inject
    @EmbeddingStoreName("ip")
    RedisEmbeddingStore ipStore;

    @Inject
    @EmbeddingStoreName("l2")
    RedisEmbeddingStore l2Store;

    @AfterEach
    void clear() {
        cosineStore.deleteAll();
        ipStore.deleteAll();
        l2Store.deleteAll();
    }

    @Test
    void cosine() {
        assertScores(cosineStore, 1.0, 0.5, 0.0);
    }

    @Test
    void innerProduct() {
        assertScores(ipStore, 1.0, 0.5, 0.0);
    }

    @Test
    void l2() {
        // squared euclidean distances are 0, 2 and 4
        assertScores(l2Store, 1.0, 1.0 / 3, 0.2);
    }

    private static void assertScores(RedisEmbeddingStore store, double same, double orthogonal, double opposite) {
        store.addAll(List.of("same", "orthogonal", "opposite"), List.of(SAME, ORTHOGONAL, OPPOSITE), null);

        List<EmbeddingMatch<TextSegment>> matches = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(QUERY)
                .maxResults(3)
                .build()).matches();

        assertThat(matches).extracting(EmbeddingMatch::embeddingId).containsExactly("same", "orthogonal", "opposite");
        assertThat(matches.get(0).score()).isCloseTo(same, within(1e-6));
        assertThat(matches.get(1).score()).isCloseTo(orthogonal, within(1e-6));
        assertThat(matches.get(2).score()).isCloseTo(opposite, within(1e-6));
    }
}
