package org.acme.example.cache.MultiEmbedding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.runtime.cache.AiCacheStore;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MultipleEmbeddingTest {

    @Inject
    AiCacheStore aiCacheStore;

    @Inject
    AiService3 service3;

    @Inject
    EmbeddingModel embeddingModel;

    @Test
    void test() {

        ArcContainer container = Arc.container();
        ManagedContext requestContext = container.requestContext();
        String cacheId = requestContext.getState() + "#" + AiService3.class.getName() + ".poem";

        service3.poem("test");
        var messages = aiCacheStore.getAll(cacheId);
        assertEquals(1, messages.size());
        assertEquals(embeddingModel.embed("test").content(), messages.get(0).embedded());
    }
}
