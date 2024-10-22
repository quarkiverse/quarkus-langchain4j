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
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.GuardrailException;
import io.quarkus.test.QuarkusUnitTest;

public class OutputGuardrailRepromptingRetryDisabledTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class,
                            MyChatModel.class, MyChatModelSupplier.class, MyMemoryProviderSupplier.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.guardrails.max-retries", "0");

    @Inject
    MyAiService aiService;

    @Test
    @ActivateRequestContext
    void testOk() {
        aiService.ok("1", "foo");
    }

    @Inject
    RetryGuardrail retryGuardrail;

    @Test
    @ActivateRequestContext
    void testRetryFailing() {
        assertThatThrownBy(() -> aiService.retry("1", "foo"))
                .isInstanceOf(GuardrailException.class)
                .hasMessageContaining("maximum number of retries");
        assertThat(retryGuardrail.getSpy()).isEqualTo(1); // No retry
    }

    @Inject
    RepromptingGuardrail repromptingGuardrail;

    @Test
    @ActivateRequestContext
    void testRepromptingFailing() {
        assertThatThrownBy(() -> aiService.reprompting("1", "foo"))
                .isInstanceOf(GuardrailException.class)
                .hasMessageContaining("maximum number of retries");
        assertThat(repromptingGuardrail.getSpy()).isEqualTo(1); // No retry
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    @SystemMessage("Say Hi!")
    public interface MyAiService {

        @OutputGuardrails(OkGuardrail.class)
        String ok(@MemoryId String mem, @dev.langchain4j.service.UserMessage String message);

        @OutputGuardrails(RetryGuardrail.class)
        String retry(@MemoryId String mem, @dev.langchain4j.service.UserMessage String message);

        @OutputGuardrails(RepromptingGuardrail.class)
        String reprompting(@MemoryId String mem, @dev.langchain4j.service.UserMessage String message);
    }

    public static class MyChatModelSupplier implements Supplier<ChatLanguageModel> {

        @Override
        public ChatLanguageModel get() {
            return new MyChatModel();
        }
    }

    @ApplicationScoped
    public static class OkGuardrail implements OutputGuardrail {

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }

    }

    @ApplicationScoped
    public static class RetryGuardrail implements OutputGuardrail {

        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(OutputGuardrailParams params) {
            int v = spy.incrementAndGet();
            return retry("Retry");
        }

        public int getSpy() {
            return spy.get();
        }
    }

    @ApplicationScoped
    public static class RepromptingGuardrail implements OutputGuardrail {

        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(OutputGuardrailParams params) {
            int v = spy.incrementAndGet();
            return reprompt("Retry", "reprompt");
        }

        public int getSpy() {
            return spy.get();
        }
    }

    public static class MyChatModel implements ChatLanguageModel {

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return new Response<>(new AiMessage("Hello"));
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
