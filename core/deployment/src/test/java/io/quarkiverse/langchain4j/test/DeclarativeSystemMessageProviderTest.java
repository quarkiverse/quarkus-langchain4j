package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests for the {@code systemMessageProviderSupplier} attribute on {@link RegisterAiService}.
 */
public class DeclarativeSystemMessageProviderTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            AssistantWithSystemMessageProvider.class,
                            MySystemMessageProvider.class,
                            MyChatModelSupplier.class,
                            MyChatModel.class));

    @Inject
    AssistantWithSystemMessageProvider assistant;

    @Test
    @ActivateRequestContext
    public void testSystemMessageProviderIsUsed() {
        String response = assistant.chat("user123", "Hello");
        assertThat(response).isEqualTo("System message for user: user123");
    }

    @Test
    @ActivateRequestContext
    public void testSystemMessageProviderWithDifferentMemoryId() {
        String response = assistant.chat("user456", "Hello");
        assertThat(response).isEqualTo("System message for user: user456");
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class, systemMessageProviderSupplier = MySystemMessageProvider.class)
    public interface AssistantWithSystemMessageProvider {
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    @ApplicationScoped
    public static class MySystemMessageProvider implements SystemMessageProvider {
        @Override
        public Optional<String> getSystemMessage(Object memoryId) {
            return Optional.of("System message for user: " + memoryId);
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            SystemMessage systemMessage = chatRequest.messages().stream()
                    .filter(SystemMessage.class::isInstance)
                    .map(SystemMessage.class::cast)
                    .findFirst()
                    .orElse(null);
            String responseText = systemMessage != null ? systemMessage.text() : "No system message";
            return ChatResponse.builder().aiMessage(new AiMessage(responseText)).build();
        }
    }
}
