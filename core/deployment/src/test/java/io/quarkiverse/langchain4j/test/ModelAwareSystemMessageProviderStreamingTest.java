package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProviderWithContext;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * Streaming counterpart of {@link ModelAwareSystemMessageProviderTest}: verifies that the
 * {@link InvocationContext} is populated from the streaming chat model (provider and default
 * request parameters) when no blocking chat model is present.
 */
public class ModelAwareSystemMessageProviderStreamingTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            Assistant.class,
                            ModelAwareProvider.class,
                            FakeClaudeStreamingModel.class));

    @Inject
    Assistant assistant;

    @Test
    @ActivateRequestContext
    public void providerReceivesModelInformationFromStreamingContext() {
        String response = String.join("",
                assistant.chat("user1", "Hello").collect().asList().await().indefinitely());
        assertThat(response).isEqualTo("provider=ANTHROPIC model=claude-opus-4-8");
    }

    @RegisterAiService(chatMemoryProvider = void.class, systemMessageProvider = ModelAwareProvider.class)
    public interface Assistant {
        Multi<String> chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    @ApplicationScoped
    public static class ModelAwareProvider implements SystemMessageProviderWithContext {

        @Override
        public Optional<String> getSystemMessage(InvocationContext context) {
            return Optional.of("provider=" + context.modelProvider()
                    + " model=" + context.defaultRequestParameters().modelName());
        }
    }

    @ApplicationScoped
    public static class FakeClaudeStreamingModel implements StreamingChatModel {

        @Override
        public ModelProvider provider() {
            return ModelProvider.ANTHROPIC;
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return ChatRequestParameters.builder().modelName("claude-opus-4-8").build();
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            SystemMessage systemMessage = chatRequest.messages().stream()
                    .filter(SystemMessage.class::isInstance)
                    .map(SystemMessage.class::cast)
                    .findFirst()
                    .orElse(null);
            String responseText = systemMessage != null ? systemMessage.text() : "No system message";
            handler.onPartialResponse(responseText);
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("")).build());
        }
    }
}
