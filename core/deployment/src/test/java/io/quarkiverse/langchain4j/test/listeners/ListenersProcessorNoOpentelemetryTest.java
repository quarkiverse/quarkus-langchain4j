package io.quarkiverse.langchain4j.test.listeners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Supplier;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.listeners.ChatModelSpanContributor;
import io.quarkiverse.langchain4j.runtime.listeners.SpanChatModelListener;
import io.quarkus.test.QuarkusUnitTest;

class ListenersProcessorNoOpentelemetryTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(AiService.class, EchoChatLanguageModelSupplier.class));

    @Inject
    AiService aiService;
    @Inject
    Instance<SpanChatModelListener> spanChatModelListenerInstance;
    @Inject
    Instance<ChatModelSpanContributor> chatModelSpanContributorInstance;

    @Test
    void shouldNotHaveSpanChatModelListenerWhenNoOtel() {
        assertThat(aiService).isNotNull();
        assertThatThrownBy(spanChatModelListenerInstance::get)
                .isInstanceOf(UnsatisfiedResolutionException.class);
        assertThatThrownBy(chatModelSpanContributorInstance::get)
                .isInstanceOf(UnsatisfiedResolutionException.class);
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatLanguageModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface AiService {
        @UserMessage("test")
        String test();
    }

    public static class EchoChatLanguageModelSupplier implements Supplier<ChatLanguageModel> {
        @Override
        public ChatLanguageModel get() {
            return (messages) -> new Response<>(new AiMessage(messages.get(0).text()));
        }
    }
}
