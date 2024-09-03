package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.GuardrailException;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrails;
import io.quarkus.test.QuarkusUnitTest;

public class GuardrailRepromptingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class,
                            MyChatModel.class, MyChatModelSupplier.class, MyMemoryProviderSupplier.class));

    @Inject
    MyAiService aiService;

    @Inject
    RepromptingOne repromptingOne;
    @Inject
    RepromptingTwo repromptingTwo;
    @Inject
    RepromptingFailed repromptingFailed;

    @Test
    @ActivateRequestContext
    void testRepromptingOkAfterOneRetry() {
        aiService.one("1", "foo");
        assertThat(repromptingOne.getSpy()).isEqualTo(2);
    }

    @Test
    @ActivateRequestContext
    void testRepromptingOkAfterTwoRetries() {
        aiService.two("2", "foo");
        assertThat(repromptingTwo.getSpy()).isEqualTo(3);
    }

    @Test
    @ActivateRequestContext
    void testRepromptingFailing() {
        assertThatThrownBy(() -> aiService.fail("3", "foo"))
                .isInstanceOf(GuardrailException.class);
        assertThat(repromptingFailed.getSpy()).isEqualTo(3);

    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    @SystemMessage("Say Hi!")
    public interface MyAiService {

        @OutputGuardrails(RepromptingOne.class)
        String one(@MemoryId String mem, @dev.langchain4j.service.UserMessage String message);

        @OutputGuardrails(RepromptingTwo.class)
        String two(@MemoryId String mem, @dev.langchain4j.service.UserMessage String message);

        @OutputGuardrails(RepromptingFailed.class)
        String fail(@MemoryId String mem, @dev.langchain4j.service.UserMessage String message);
    }

    public static class MyChatModelSupplier implements Supplier<ChatLanguageModel> {

        @Override
        public ChatLanguageModel get() {
            return new MyChatModel();
        }
    }

    @ApplicationScoped
    public static class RepromptingOne implements OutputGuardrail {

        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public void validate(AiMessage responseFromLLM) throws ValidationException {
            if (spy.incrementAndGet() == 1) {
                throw new ValidationException("Retry", true, "Retry");
            }
            // OK
        }

        public int getSpy() {
            return spy.get();
        }
    }

    @ApplicationScoped
    public static class RepromptingTwo implements OutputGuardrail {

        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public void validate(OutputGuardrailParams params) throws ValidationException {
            int v = spy.incrementAndGet();
            List<ChatMessage> messages = params.memory().messages();
            if (v == 1) {
                ChatMessage last = messages.get(messages.size() - 1);
                assertThat(last).isInstanceOf(AiMessage.class);
                assertThat(((AiMessage) last).text()).isEqualTo("Nope");
                throw new ValidationException("Retry", true, "Retry");
            }
            if (v == 2) {
                // Check that it's in memory
                ChatMessage last = messages.get(messages.size() - 1);
                ChatMessage beforeLast = messages.get(messages.size() - 2);

                assertThat(last).isInstanceOf(AiMessage.class);
                assertThat(((AiMessage) last).text()).isEqualTo("Hello");
                assertThat(beforeLast).isInstanceOf(UserMessage.class);
                assertThat(beforeLast.text()).isEqualTo("Retry");

                throw new ValidationException("Retry", true, "Retry");
            }
            if (v != 3) {
                throw new IllegalArgumentException("Unexpected call");
            }
            // OK
        }

        public int getSpy() {
            return spy.get();
        }
    }

    @ApplicationScoped
    public static class RepromptingFailed implements OutputGuardrail {

        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public void validate(OutputGuardrailParams params) throws ValidationException {
            int v = spy.incrementAndGet();
            List<ChatMessage> messages = params.memory().messages();
            if (v == 1) {
                ChatMessage last = messages.get(messages.size() - 1);
                assertThat(last).isInstanceOf(AiMessage.class);
                assertThat(((AiMessage) last).text()).isEqualTo("Nope");
                throw new ValidationException("Retry", true, "Retry Once");
            }
            if (v == 2) {
                // Check that it's in memory
                ChatMessage last = messages.get(messages.size() - 1);
                ChatMessage beforeLast = messages.get(messages.size() - 2);

                assertThat(last).isInstanceOf(AiMessage.class);
                assertThat(((AiMessage) last).text()).isEqualTo("Hello");
                assertThat(beforeLast).isInstanceOf(UserMessage.class);
                assertThat(beforeLast.text()).isEqualTo("Retry Once");
                throw new ValidationException("Retry", true, "Retry Twice");
            }
            throw new ValidationException("Retry", true, "Retry Again");
        }

        public int getSpy() {
            return spy.get();
        }
    }

    public static class MyChatModel implements ChatLanguageModel {

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            ChatMessage last = messages.get(messages.size() - 1);
            if (last instanceof UserMessage && last.text().equals("foo")) {
                return new Response<>(new AiMessage("Nope"));
            }
            if (last instanceof UserMessage && last.text().contains("Retry")) {
                return new Response<>(new AiMessage("Hello"));
            }
            throw new IllegalArgumentException("Unexpected message: " + messages);
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        private final Map<String, ChatMemory> memories = new HashMap<>();

        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return memories.computeIfAbsent(memoryId.toString(), k -> MessageWindowChatMemory.withMaxMessages(10));
                }
            };
        }
    }
}
