package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

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
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProviderWithContext;
import io.quarkus.test.QuarkusUnitTest;

public class SystemMessageAnnotationPrecedenceTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            Assistant.class,
                            ProviderShouldNotWin.class,
                            ModelSupplier.class,
                            EchoModel.class));

    @Inject
    Assistant assistant;

    @Test
    @ActivateRequestContext
    public void annotationWinsOverProvider() {
        assertThat(assistant.chat("Hello")).isEqualTo("from annotation");
    }

    @RegisterAiService(chatLanguageModelSupplier = ModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class, systemMessageProviderSupplier = ProviderShouldNotWin.class)
    public interface Assistant {
        @dev.langchain4j.service.SystemMessage("from annotation")
        String chat(@UserMessage String userMessage);
    }

    @ApplicationScoped
    public static class ProviderShouldNotWin implements SystemMessageProviderWithContext {

        @Override
        public Optional<String> getSystemMessage(InvocationContext context) {
            return Optional.of("from provider (with context)");
        }
    }

    @Singleton
    public static class ModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new EchoModel();
        }
    }

    public static class EchoModel implements ChatModel {

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
