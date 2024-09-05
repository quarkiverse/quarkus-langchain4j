package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
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
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.GuardrailException;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkus.test.QuarkusUnitTest;

public class OutputGuardrailValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, OKGuardrail.class, KOGuardrail.class,
                            MyChatModel.class, MyChatModelSupplier.class, MyMemoryProviderSupplier.class));

    @Inject
    MyAiService aiService;

    @Test
    @ActivateRequestContext
    void testOk() {
        aiService.ok("1");
    }

    @Test
    @ActivateRequestContext
    void testKo() {
        assertThatThrownBy(() -> aiService.ko("2"))
                .isInstanceOf(GuardrailException.class)
                .hasMessageContaining("KO");
    }

    @Inject
    RetryingGuardrail retry;

    @Test
    @ActivateRequestContext
    void testRetryOk() {
        aiService.retry("3");
        assertThat(retry.spy()).isEqualTo(2);
    }

    @Inject
    RetryingButFailGuardrail retryFail;

    @Test
    @ActivateRequestContext
    void testRetryFail() {
        assertThatThrownBy(() -> aiService.retryButFail("4"))
                .isInstanceOf(GuardrailException.class)
                .hasMessageContaining("maximum number of retries");
        assertThat(retryFail.spy()).isEqualTo(3);
    }

    @Inject
    KOFatalGuardrail fatal;

    @Test
    @ActivateRequestContext
    void testFatalException() {
        assertThatThrownBy(() -> aiService.fatal("5"))
                .isInstanceOf(GuardrailException.class)
                .hasMessageContaining("Fatal");
        assertThat(fatal.spy()).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void testRepromptingWithoutRetry() {
        assertThatThrownBy(() -> aiService.repromptingWithoutRetry("6"))
                .isInstanceOf(GuardrailException.class)
                .hasCauseExactlyInstanceOf(IllegalArgumentException.class);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @UserMessage("Say Hi!")
        @OutputGuardrails(OKGuardrail.class)
        String ok(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOGuardrail.class)
        String ko(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(RetryingGuardrail.class)
        String retry(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(RetryingButFailGuardrail.class)
        String retryButFail(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOFatalGuardrail.class)
        String fatal(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(RepromptingWithoutRetryGuardrail.class)
        String repromptingWithoutRetry(@MemoryId String mem);
    }

    @RequestScoped
    public static class OKGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public void validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
        }

        public int spy() {
            return spy.get();
        }
    }

    @ApplicationScoped
    public static class KOGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public void validate(AiMessage responseFromLLM) throws ValidationException {
            spy.incrementAndGet();
            throw new ValidationException("KO", false, null);
        }

        public int spy() {
            return spy.get();
        }
    }

    @ApplicationScoped
    public static class RetryingGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public void validate(AiMessage responseFromLLM) throws ValidationException {
            int v = spy.incrementAndGet();
            if (v == 2) {
                return;
            }
            throw new ValidationException("KO", true, null);
        }

        public int spy() {
            return spy.get();
        }
    }

    @ApplicationScoped
    public static class RetryingButFailGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public void validate(AiMessage responseFromLLM) throws ValidationException {
            int v = spy.incrementAndGet();
            throw new ValidationException("KO", true, null);
        }

        public int spy() {
            return spy.get();
        }
    }

    @ApplicationScoped
    public static class RepromptingWithoutRetryGuardrail implements OutputGuardrail {

        @Override
        public void validate(AiMessage responseFromLLM) throws ValidationException {
            throw new ValidationException("KO", false, "Reprompt");
        }

    }

    @ApplicationScoped
    public static class KOFatalGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public void validate(AiMessage responseFromLLM) throws ValidationException {
            spy.incrementAndGet();
            throw new IllegalArgumentException("Fatal");
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatLanguageModel> {

        @Override
        public ChatLanguageModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatLanguageModel {

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return new Response<>(new AiMessage("Hi!"));
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return new NoopChatMemory();
                }
            };
        }
    }
}
