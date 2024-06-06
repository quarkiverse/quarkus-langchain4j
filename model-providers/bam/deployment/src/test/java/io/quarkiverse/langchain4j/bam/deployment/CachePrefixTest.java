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
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.CacheResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.cache.AiCacheStore;
import io.quarkus.test.QuarkusUnitTest;

public class CachePrefixTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.base-url", WireMockUtil.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.cache.embedding.passage-prefix", "passage: ")
            .overrideRuntimeConfigKey("quarkus.langchain4j.cache.embedding.query-prefix", "query: ")
            .overrideRuntimeConfigKey("quarkus.langchain4j.cache.ttl", "2s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.cache.max-size", "3")
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Singleton
    interface LLMService {

        @SystemMessage("TEST")
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

                if (textSegments.get(0).text().equals("passage: TESTfirstMessage")) {
                    assertEquals("passage: TESTfirstMessage", textSegments.get(0).text());
                    return Response.from(List.of(Embedding.from(firstMessage)));
                } else if (textSegments.get(0).text().equals("query: TESTfirstMessage")) {
                    assertEquals("query: TESTfirstMessage", textSegments.get(0).text());
                    return Response.from(List.of(Embedding.from(firstMessage)));
                } else if (textSegments.get(0).text().equals("passage: TESTsecondMessage")) {
                    assertEquals("passage: TESTsecondMessage", textSegments.get(0).text());
                    return Response.from(List.of(Embedding.from(secondMessage)));
                } else if (textSegments.get(0).text().equals("query: TESTsecondMessage")) {
                    assertEquals("query: TESTsecondMessage", textSegments.get(0).text());
                    return Response.from(List.of(Embedding.from(secondMessage)));
                }

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
    void cache_prefix_test() throws InterruptedException {

        String cacheId = "default";
        aiCacheStore.deleteCache(cacheId);

        service.chat("firstMessage");
        service.chat("secondMessage");
        assertEquals(2, aiCacheStore.getAll(cacheId).size());
        assertEquals("cache: TESTfirstMessage", aiCacheStore.getAll(cacheId).get(0).response().text());
        assertEquals(firstMessage, aiCacheStore.getAll(cacheId).get(0).embedded().vector());
        assertEquals("cache: TESTsecondMessage", aiCacheStore.getAll(cacheId).get(1).response().text());
        assertEquals(secondMessage, aiCacheStore.getAll(cacheId).get(1).embedded().vector());
    }

    static float[] firstMessage = {
            0.039016734808683395f,
            0.010098248720169067f,
            -0.02687959559261799f,
    };

    static float[] secondMessage = {
            0.139016734108685515f,
            0.211198249720169167f,
            0.62687959559261799f,
    };
}
