package io.quarkiverse.langchain4j.test.listeners;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

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
import io.quarkiverse.langchain4j.runtime.listeners.SpanChatModelListener;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;

class ListenersProcessorOnlySpanChatModelListenerTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(AiService.class, EchoChatLanguageModelSupplier.class))
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-opentelemetry", "3.15.2")));

    @Inject
    AiService aiService;
    @Inject
    SpanChatModelListener spanChatModelListener;

    @Test
    void shouldNotHaveSpanChatModelListenerWhenNoOtel() {
        assertThat(aiService).isNotNull();
        assertThat(spanChatModelListener).isNotNull();
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
