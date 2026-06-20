package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests the direct bean-class attribute pattern on {@link RegisterAiService}.
 * <p>
 * Exercises the tri-state resolution model:
 * <ul>
 * <li>{@code void.class} = SKIP (disabled)</li>
 * <li>Interface type (e.g. {@code ChatMemoryProvider.class}) = AUTO_DISCOVER</li>
 * <li>Concrete class = EXPLICIT (inject specific CDI bean)</li>
 * </ul>
 */
public class DirectBeanClassAttributeTest {

    @ApplicationScoped
    public static class FixedChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(new AiMessage("fixed-response"))
                    .build();
        }
    }

    @ApplicationScoped
    public static class ExplicitMemoryProvider implements ChatMemoryProvider {
        @Override
        public dev.langchain4j.memory.ChatMemory get(Object memoryId) {
            return MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(5)
                    .chatMemoryStore(new InMemoryChatMemoryStore())
                    .build();
        }
    }

    // Concrete class → EXPLICIT resolution
    @RegisterAiService(chatMemoryProvider = ExplicitMemoryProvider.class)
    interface ServiceWithExplicitMemory {
        String chat(String msg);
    }

    // void.class → SKIP (no memory)
    @RegisterAiService(chatMemoryProvider = void.class)
    interface ServiceWithNoMemory {
        String chat(String msg);
    }

    // Default (no attribute) → AUTO_DISCOVER since default is ChatMemoryProvider.class
    @RegisterAiService
    interface ServiceWithAutoDiscoverMemory {
        String chat(String msg);
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            FixedChatModel.class,
                            ExplicitMemoryProvider.class,
                            ServiceWithExplicitMemory.class,
                            ServiceWithNoMemory.class,
                            ServiceWithAutoDiscoverMemory.class));

    @Inject
    ServiceWithExplicitMemory explicitService;

    @Inject
    ServiceWithNoMemory noMemoryService;

    @Inject
    ServiceWithAutoDiscoverMemory autoDiscoverService;

    @Test
    @ActivateRequestContext
    public void explicitMemoryProviderIsInjected() {
        String response = explicitService.chat("hello");
        assertThat(response).isEqualTo("fixed-response");
    }

    @Test
    @ActivateRequestContext
    public void noMemoryServiceWorks() {
        String response = noMemoryService.chat("hello");
        assertThat(response).isEqualTo("fixed-response");
    }

    @Test
    @ActivateRequestContext
    public void autoDiscoverMemoryServiceWorks() {
        String response = autoDiscoverService.chat("hello");
        assertThat(response).isEqualTo("fixed-response");
    }
}
