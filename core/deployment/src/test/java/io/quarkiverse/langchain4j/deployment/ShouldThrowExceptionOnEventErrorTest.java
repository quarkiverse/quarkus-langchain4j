package io.quarkiverse.langchain4j.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

class ShouldThrowExceptionOnEventErrorTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            NormalAssistant.class,
                            DoesntThrowExceptionsAssistant.class,
                            ThrowsExceptionsAssistant.class,
                            EventListener.class));

    @Inject
    NormalAssistant normalAssistant;

    @Inject
    DoesntThrowExceptionsAssistant doesntThrowExceptionsAssistant;

    @Inject
    ThrowsExceptionsAssistant throwsExceptionsAssistant;

    @Inject
    EventListener eventListener;

    @BeforeEach
    void beforeEach() {
        eventListener.reset();
    }

    @Test
    void normalAssistant() {
        assertNoExceptionThrown(normalAssistant);
    }

    @Test
    void doesntThrowExceptionsAssistant() {
        assertNoExceptionThrown(doesntThrowExceptionsAssistant);
    }

    @Test
    void throwsExceptionsAssistant() {
        assertExceptionThrown(throwsExceptionsAssistant);
    }

    private void assertNoExceptionThrown(Assistant assistant) {
        assertThat(assistant.chat("Hello")).isEqualTo("Hello");
        assertThat(eventListener.spy()).isEqualTo(1);
    }

    private void assertExceptionThrown(Assistant assistant) {
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> assistant.chat("Hello"))
                .withMessage("Some error");

        assertThat(eventListener.spy()).isEqualTo(1);
    }

    @ApplicationScoped
    public static class EventListener {
        private static final AtomicInteger COUNTER = new AtomicInteger();

        public void onEvent(@Observes AiServiceStartedEvent event) {
            COUNTER.incrementAndGet();
            throw new RuntimeException("Some error");
        }

        public void reset() {
            COUNTER.set(0);
        }

        public int spy() {
            return COUNTER.get();
        }
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = AssistantChatModeSupplier.class)
    interface NormalAssistant extends Assistant {
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = AssistantChatModeSupplier.class, shouldThrowExceptionOnEventError = false)
    interface DoesntThrowExceptionsAssistant extends Assistant {
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = AssistantChatModeSupplier.class, shouldThrowExceptionOnEventError = true)
    interface ThrowsExceptionsAssistant extends Assistant {
    }

    interface Assistant {
        String chat(String message);
    }

    public static class AssistantChatModeSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new AssistantChatModel();
        }
    }

    public static class AssistantChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("Hello"))
                    .finishReason(FinishReason.STOP)
                    .build();
        }
    }
}
