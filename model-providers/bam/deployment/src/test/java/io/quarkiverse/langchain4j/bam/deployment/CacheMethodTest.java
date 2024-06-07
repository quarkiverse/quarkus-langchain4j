package io.quarkiverse.langchain4j.bam.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
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
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.QuarkusUnitTest;

public class CacheMethodTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.base-url", WireMockUtil.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.api-key", WireMockUtil.API_KEY)
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @RegisterAiService
    @Singleton
    interface LLMService {

        @SystemMessage("This is a systemMessage")
        @UserMessage("This is a userMessage {text}")
        @CacheResult
        String chat(String text);

        @SystemMessage("This is a systemMessage")
        @UserMessage("This is a userMessage {text}")
        String chatNoCache(String text);

        @Singleton
        public class CustomChatModel implements ChatLanguageModel {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                return Response.from(AiMessage.from("result"));
            }
        }

        @Singleton
        public class CustomEmbedding implements EmbeddingModel {

            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
                return Response.from(List.of(Embedding.from(es)));
            }
        }
    }

    @Inject
    LLMService service;

    @Inject
    AiCacheStore aiCacheStore;

    @Test
    void cache_test() {

        String chatCacheId = "#" + LLMService.class.getName() + ".chat";
        String chatNoCacheCacheId = "#" + LLMService.class.getName() + ".chatNoCache";

        assertEquals(0, aiCacheStore.getAll(chatCacheId).size());
        assertEquals(0, aiCacheStore.getAll(chatNoCacheCacheId).size());
        service.chatNoCache("noCache");
        assertEquals(0, aiCacheStore.getAll(chatCacheId).size());
        assertEquals(0, aiCacheStore.getAll(chatNoCacheCacheId).size());

        service.chat("cache");
        assertEquals(1, aiCacheStore.getAll(chatCacheId).size());
        assertEquals("result", aiCacheStore.getAll(chatCacheId).get(0).response().text());
        assertEquals(es, aiCacheStore.getAll(chatCacheId).get(0).embedded().vector());
        assertEquals(0, aiCacheStore.getAll(chatNoCacheCacheId).size());
    }

    @Test
    @ActivateRequestContext
    void cache_test_with_request_context() {

        ArcContainer container = Arc.container();
        ManagedContext requestContext = container.requestContext();
        String chatNoCacheId = requestContext.getState() + "#" + LLMService.class.getName() + ".chatNoCache";
        String chatCacheId = requestContext.getState() + "#" + LLMService.class.getName() + ".chat";

        assertEquals(0, aiCacheStore.getAll(chatNoCacheId).size());
        service.chatNoCache("noCache");
        assertEquals(0, aiCacheStore.getAll(chatNoCacheId).size());

        service.chat("cache");
        assertEquals(1, aiCacheStore.getAll(chatCacheId).size());
        assertEquals("result", aiCacheStore.getAll(chatCacheId).get(0).response().text());
        assertEquals(es, aiCacheStore.getAll(chatCacheId).get(0).embedded().vector());
    }

    static float[] es = {
            0.039016734808683395f,
            0.010098248720169067f,
            -0.02687959559261799f
    };
}
