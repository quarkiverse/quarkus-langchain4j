package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Produces;
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
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProviderWithContext;
import io.quarkus.arc.Unremovable;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that {@link SystemMessageProviderWithContext} distinguishes between two models from the
 * same {@link ModelProvider}, based on {@code InvocationContext.defaultRequestParameters().modelName()}.
 */
public class ModelAwareSystemMessageProviderSameProviderModelNameTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            Assistant.class,
                            ModelAwareProvider.class,
                            DefaultModelSupplier.class,
                            NamedModelProducer.class,
                            EchoModel.class));

    @Inject
    Assistant assistant;

    @Test
    @ActivateRequestContext
    public void promptReflectsPerCallModelNameEvenWithSameProvider() {
        assertThat(assistant.chat("Hello", "nano")).isEqualTo("provider=OPEN_AI model=gpt-4.1-nano");
        assertThat(assistant.chat("Hello", "4o")).isEqualTo("provider=OPEN_AI model=gpt-4o");
    }

    @RegisterAiService(chatLanguageModelSupplier = DefaultModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class, systemMessageProviderSupplier = ModelAwareProvider.class)
    public interface Assistant {
        String chat(@UserMessage String userMessage, @ModelName String model);
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
    public static class DefaultModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new EchoModel(ModelProvider.MISTRAL_AI, "default-model");
        }
    }

    @Singleton
    public static class NamedModelProducer {

        @Produces
        @Singleton
        @Unremovable
        @ModelName("nano")
        public ChatModel nano() {
            return new EchoModel(ModelProvider.OPEN_AI, "gpt-4.1-nano");
        }

        @Produces
        @Singleton
        @Unremovable
        @ModelName("4o")
        public ChatModel fourO() {
            return new EchoModel(ModelProvider.OPEN_AI, "gpt-4o");
        }
    }

    public static class EchoModel implements ChatModel {

        private final ModelProvider provider;
        private final String modelName;

        public EchoModel(ModelProvider provider, String modelName) {
            this.provider = provider;
            this.modelName = modelName;
        }

        @Override
        public ModelProvider provider() {
            return provider;
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return ChatRequestParameters.builder().modelName(modelName).build();
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
