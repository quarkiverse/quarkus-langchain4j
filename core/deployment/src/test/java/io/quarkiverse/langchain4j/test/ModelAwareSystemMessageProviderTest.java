package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProviderWithContext;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests that a {@link SystemMessageProvider} can produce a system message based on the
 * model in use, by inspecting the {@link InvocationContext} (model provider and default
 * request parameters such as the model name).
 */
public class ModelAwareSystemMessageProviderTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            Assistant.class,
                            ModelAwareProvider.class,
                            ClaudeModelSupplier.class,
                            FakeClaudeModel.class));

    @Inject
    Assistant assistant;

    @Test
    @ActivateRequestContext
    public void providerReceivesModelInformationFromContext() {
        String response = assistant.chat("user1", "Hello");
        assertThat(response).isEqualTo("provider=ANTHROPIC model=claude-opus-4-8");
    }

    @RegisterAiService(chatLanguageModelSupplier = ClaudeModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class, systemMessageProviderSupplier = ModelAwareProvider.class)
    public interface Assistant {
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    @ApplicationScoped
    public static class ModelAwareProvider implements SystemMessageProviderWithContext {

        @Override
        public Optional<String> getSystemMessage(InvocationContext context) {
            return Optional.of("provider=" + context.modelProvider()
                    + " model=" + context.defaultRequestParameters().modelName());
        }
    }

    @Singleton
    public static class ClaudeModelSupplier implements Supplier<ChatModel> {

        private FakeClaudeModel model;

        @PostConstruct
        public void init() {
            model = new FakeClaudeModel();
        }

        @Override
        public ChatModel get() {
            return model;
        }
    }

    public static class FakeClaudeModel implements ChatModel {

        @Override
        public ModelProvider provider() {
            return ModelProvider.ANTHROPIC;
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return ChatRequestParameters.builder().modelName("claude-opus-4-8").build();
        }

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
