package io.quarkiverse.langchain4j.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusUnitTest;

public class Neo4jDefaultAndNamedStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.neo4j.dimension", "384")
            .overrideConfigKey("quarkus.langchain4j.neo4j.named-store.database-name", "neo4j")
            .overrideRuntimeConfigKey("quarkus.langchain4j.neo4j.named-store.dimension", "1536")
            .overrideRuntimeConfigKey("quarkus.langchain4j.neo4j.named-store.label", "Custom")
            .overrideRuntimeConfigKey("quarkus.langchain4j.neo4j.named-store.index-name", "custom_vector");

    @Inject
    EmbeddingStore<TextSegment> defaultEmbeddingStore;

    @Inject
    @EmbeddingStoreName("named-store")
    EmbeddingStore<TextSegment> namedEmbeddingStore;

    @Test
    void testDefault() {
        assertThat(defaultEmbeddingStore).isNotNull();
    }

    @Test
    void testNamed() {
        assertThat(namedEmbeddingStore).isNotNull();
    }

    @Test
    void testNotSame() {
        assertThat(defaultEmbeddingStore).isNotSameAs(namedEmbeddingStore);
    }
}
