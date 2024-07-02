package io.quarkiverse.langchain4j.bam.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.CacheResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.cache.AiCacheStore;
import io.quarkus.test.QuarkusUnitTest;

public class CacheConfigTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.base-url", WireMockUtil.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.cache.ttl", "2s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.cache.max-size", "3")
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Singleton
    interface LLMService {

        @UserMessage("{text}")
        @CacheResult
        String chat(String text);

        @Singleton
        public class CustomChatModel implements ChatLanguageModel {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                String m = messages.stream().map(ChatMessage::text).collect(Collectors.joining(""));
                return Response.from(AiMessage.from("cache: " + m));
            }
        }

        @Singleton
        public class CustomEmbedding implements EmbeddingModel {

            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
                if (textSegments.get(0).text().equals("FIRST"))
                    return Response.from(List.of(Embedding.from(first)));
                else if (textSegments.get(0).text().equals("SECOND"))
                    return Response.from(List.of(Embedding.from(second)));
                else if (textSegments.get(0).text().equals("THIRD"))
                    return Response.from(List.of(Embedding.from(third)));
                else if (textSegments.get(0).text().equals("FOURTH"))
                    return Response.from(List.of(Embedding.from(fourth)));
                return null;
            }
        }
    }

    @Inject
    LLMService service;

    @Inject
    AiCacheStore aiCacheStore;

    @Test
    @Order(1)
    void cache_ttl_test() throws InterruptedException {

        String cacheId = "default";
        aiCacheStore.deleteCache(cacheId);

        service.chat("FIRST");
        service.chat("SECOND");
        assertEquals(2, aiCacheStore.getAll(cacheId).size());
        assertEquals("cache: FIRST", aiCacheStore.getAll(cacheId).get(0).response().text());
        assertEquals(first, aiCacheStore.getAll(cacheId).get(0).embedded().vector());
        assertEquals("cache: SECOND", aiCacheStore.getAll(cacheId).get(1).response().text());
        assertEquals(second, aiCacheStore.getAll(cacheId).get(1).embedded().vector());

        Thread.sleep(3000);
        service.chat("THIRD");
        assertEquals(1, aiCacheStore.getAll(cacheId).size());
        assertEquals("cache: THIRD", aiCacheStore.getAll(cacheId).get(0).response().text());
        assertEquals(third, aiCacheStore.getAll(cacheId).get(0).embedded().vector());
    }

    @Test
    @Order(2)
    void cache_max_size_test() {

        String cacheId = "default";
        aiCacheStore.deleteCache(cacheId);

        service.chat("FIRST");
        assertEquals(1, aiCacheStore.getAll(cacheId).size());
        assertEquals("cache: FIRST", aiCacheStore.getAll(cacheId).get(0).response().text());
        assertEquals(first, aiCacheStore.getAll(cacheId).get(0).embedded().vector());

        service.chat("SECOND");
        service.chat("THIRD");
        service.chat("FOURTH");
        assertEquals(3, aiCacheStore.getAll(cacheId).size());
        assertEquals("cache: SECOND", aiCacheStore.getAll(cacheId).get(0).response().text());
        assertEquals(second, aiCacheStore.getAll(cacheId).get(0).embedded().vector());
        assertEquals("cache: THIRD", aiCacheStore.getAll(cacheId).get(1).response().text());
        assertEquals(third, aiCacheStore.getAll(cacheId).get(1).embedded().vector());
        assertEquals("cache: FOURTH", aiCacheStore.getAll(cacheId).get(2).response().text());
        assertEquals(fourth, aiCacheStore.getAll(cacheId).get(2).embedded().vector());
    }

    static float[] first = {
            0.039016734808683395f,
            0.010098248720169067f,
            -0.02687959559261799f,
    };

    static float[] second = {
            0.139016734108685515f,
            0.211198249720169167f,
            0.62687959559261799f,
    };

    static float[] third = {
            -0.229016734199685515f,
            -0.211198249721169127f,
            -0.62999959559261719f,
    };

    static float[] fourth = {
            -1.229016734199685515f,
            0.211198249721169127f,
            3.62999959559261719f,
    };
}
