package io.quarkiverse.langchain4j.test.classform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The scope of a class-based AI service rules its chat memory, as for an interface: a request-scoped service, the
 * default, keeps its memory for the request and evicts it at its end; an application-scoped or singleton one shares
 * its memory across requests, isolated by {@code @MemoryId}.
 */
public class ClassBasedAiServiceScopesTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RequestAssistant.class, SharedAssistant.class, SingletonAssistant.class,
                            CountingModelSupplier.class, MemorySupplier.class));

    /** Replies "ok" and records how many messages each request carried. */
    public static class CountingModelSupplier implements Supplier<ChatModel> {

        static final List<Integer> MESSAGE_COUNTS = Collections.synchronizedList(new ArrayList<>());

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    MESSAGE_COUNTS.add(request.messages().size());
                    return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
                }
            };
        }
    }

    /** Memories backed by one store shared by every service instance, so that an evicted memory is observable. */
    public static class MemorySupplier implements Supplier<ChatMemoryProvider> {

        static final InMemoryChatMemoryStore STORE = new InMemoryChatMemoryStore();

        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return MessageWindowChatMemory.builder().id(memoryId).maxMessages(10).chatMemoryStore(STORE).build();
                }
            };
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = CountingModelSupplier.class, chatMemoryProviderSupplier = MemorySupplier.class)
    public static class RequestAssistant {

        public native String chat(@UserMessage String message);
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = CountingModelSupplier.class, chatMemoryProviderSupplier = MemorySupplier.class)
    public static class SharedAssistant {

        public native String chat(@MemoryId String user, @UserMessage String message);
    }

    @Singleton
    @RegisterAiService(chatLanguageModelSupplier = CountingModelSupplier.class, chatMemoryProviderSupplier = MemorySupplier.class)
    public static class SingletonAssistant {

        public native String chat(@MemoryId String user, @UserMessage String message);
    }

    @Inject
    RequestAssistant requestAssistant;

    @Inject
    SharedAssistant sharedAssistant;

    @Inject
    SingletonAssistant singletonAssistant;

    @BeforeEach
    void reset() {
        CountingModelSupplier.MESSAGE_COUNTS.clear();
    }

    @Test
    void aRequestScopedServiceKeepsItsMemoryForTheRequestAndEvictsItAtItsEnd() {
        inRequest(() -> {
            requestAssistant.chat("one");
            requestAssistant.chat("two");
        });
        inRequest(() -> requestAssistant.chat("three"));
        // the second call of the first request sees its user and AI messages, the next request starts afresh
        assertThat(CountingModelSupplier.MESSAGE_COUNTS).containsExactly(1, 3, 1);
    }

    @Test
    void anApplicationScopedServiceSharesItsMemoryAcrossRequestsByMemoryId() {
        inRequest(() -> sharedAssistant.chat("shared-1", "one"));
        inRequest(() -> {
            sharedAssistant.chat("shared-1", "two");
            sharedAssistant.chat("shared-2", "three");
        });
        assertThat(CountingModelSupplier.MESSAGE_COUNTS).containsExactly(1, 3, 1);
    }

    @Test
    void aSingletonServiceBehavesLikeAnApplicationScopedOne() {
        inRequest(() -> singletonAssistant.chat("singleton-1", "one"));
        inRequest(() -> {
            singletonAssistant.chat("singleton-1", "two");
            singletonAssistant.chat("singleton-2", "three");
        });
        assertThat(CountingModelSupplier.MESSAGE_COUNTS).containsExactly(1, 3, 1);
    }

    private static void inRequest(Runnable runnable) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            runnable.run();
        } finally {
            requestContext.terminate();
        }
    }
}
