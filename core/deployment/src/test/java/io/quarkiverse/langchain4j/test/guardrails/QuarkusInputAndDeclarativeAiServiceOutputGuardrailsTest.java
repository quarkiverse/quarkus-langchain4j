package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.InputGuardrails;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrails;
import io.quarkus.test.QuarkusUnitTest;

/**
 * @deprecated These tests will go away once the Quarkus-specific guardrail implementation has been fully removed
 */
@Deprecated(forRemoval = true)
public class QuarkusInputAndDeclarativeAiServiceOutputGuardrailsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class,
                            MyChatModel.class, MyChatModelSupplier.class, MyMemoryProviderSupplier.class,
                            ValidationException.class));

    @Inject
    MyOkInputGuardrail okIn;

    @Inject
    MyKoInputGuardrail koIn;

    @Inject
    MyOkOutputGuardrail okOut;

    @Inject
    MyKoOutputGuardrail koOut;

    @Inject
    MyKoWithRetryOutputGuardrail koOutWithRetry;

    @Inject
    MyKoWithRepromprOutputGuardrail koOutWithReprompt;

    @Inject
    MyAiService service;

    @Test
    @ActivateRequestContext
    void testOk() {
        assertThat(okIn.getSpy()).isEqualTo(0);
        assertThat(okOut.getSpy()).isEqualTo(0);
        service.bothOk("1", "foo");
        assertThat(okIn.getSpy()).isEqualTo(1);
        assertThat(okOut.getSpy()).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void testInKo() {
        assertThat(koIn.getSpy()).isEqualTo(0);
        assertThat(okOut.getSpy()).isEqualTo(0);
        assertThatThrownBy(() -> service.inKo("2", "foo"))
                .hasRootCauseMessage("boom");
        assertThat(koIn.getSpy()).isEqualTo(1);
        assertThat(okOut.getSpy()).isEqualTo(0);
    }

    @Test
    @ActivateRequestContext
    void testOutKo() {
        assertThat(okIn.getSpy()).isEqualTo(0);
        assertThat(koOut.getSpy()).isEqualTo(0);
        assertThatThrownBy(() -> service.outKo("2", "foo"))
                .hasRootCauseMessage("boom");
        assertThat(okIn.getSpy()).isEqualTo(1);
        assertThat(koOut.getSpy()).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void testRetry() {
        assertThat(okIn.getSpy()).isEqualTo(0);
        assertThat(koOutWithRetry.getSpy()).isEqualTo(0);
        service.outKoWithRetry("2", "foo");
        assertThat(okIn.getSpy()).isEqualTo(1);
        assertThat(koOutWithRetry.getSpy()).isEqualTo(2);
    }

    @Test
    @ActivateRequestContext
    void testReprompt() {
        assertThat(okIn.getSpy()).isEqualTo(0);
        assertThat(koOutWithReprompt.getSpy()).isEqualTo(0);
        service.outKoWithReprompt("2", "foo");
        assertThat(okIn.getSpy()).isEqualTo(1);
        assertThat(koOutWithReprompt.getSpy()).isEqualTo(2);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @InputGuardrails(MyOkInputGuardrail.class)
        @OutputGuardrails(MyOkOutputGuardrail.class)
        String bothOk(@MemoryId String id, @UserMessage String message);

        @InputGuardrails(MyKoInputGuardrail.class)
        @OutputGuardrails(MyOkOutputGuardrail.class)
        String inKo(@MemoryId String id, @UserMessage String message);

        @InputGuardrails(MyOkInputGuardrail.class)
        @OutputGuardrails(MyKoOutputGuardrail.class)
        String outKo(@MemoryId String id, @UserMessage String message);

        @InputGuardrails(MyOkInputGuardrail.class)
        @OutputGuardrails(MyKoWithRetryOutputGuardrail.class)
        String outKoWithRetry(@MemoryId String id, @UserMessage String message);

        @InputGuardrails(MyOkInputGuardrail.class)
        @OutputGuardrails(MyKoWithRepromprOutputGuardrail.class)
        String outKoWithReprompt(@MemoryId String id, @UserMessage String message);

    }

    @RequestScoped
    public static class MyOkInputGuardrail implements InputGuardrail {

        AtomicInteger spy = new AtomicInteger();

        @Override
        public InputGuardrailResult validate(InputGuardrailParams params) {
            spy.incrementAndGet();
            return success();
        }

        public int getSpy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class MyKoInputGuardrail implements InputGuardrail {

        AtomicInteger spy = new AtomicInteger();

        @Override
        public InputGuardrailResult validate(InputGuardrailParams params) {
            spy.incrementAndGet();
            return failure("boom", new ValidationException("boom"));
        }

        public int getSpy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class MyOkOutputGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailParams params) {
            spy.incrementAndGet();
            return OutputGuardrailResult.success();
        }

        public int getSpy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class MyKoOutputGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailParams params) {
            spy.incrementAndGet();
            return failure("boom", new ValidationException("boom"));
        }

        public int getSpy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class MyKoWithRetryOutputGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailParams params) {
            if (spy.incrementAndGet() == 1) {
                return retry("KO");
            }
            return success();
        }

        public int getSpy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class MyKoWithRepromprOutputGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailParams params) {
            if (spy.incrementAndGet() == 1) {
                return reprompt("KO", "retry");
            }
            return success();
        }

        public int getSpy() {
            return spy.get();
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("Hi!")).build();
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return new MessageWindowChatMemory.Builder().maxMessages(5).build();
                }
            };
        }
    }
}
