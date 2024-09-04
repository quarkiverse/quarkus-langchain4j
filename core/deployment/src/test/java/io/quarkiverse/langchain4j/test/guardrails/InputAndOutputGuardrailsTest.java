package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
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
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.InputGuardrails;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrails;
import io.quarkus.test.QuarkusUnitTest;

public class InputAndOutputGuardrailsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class,
                            MyChatModel.class, MyChatModelSupplier.class, MyMemoryProviderSupplier.class));

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
        public void validate(InputGuardrailParams params) throws ValidationException {
            spy.incrementAndGet();
        }

        public int getSpy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class MyKoInputGuardrail implements InputGuardrail {

        AtomicInteger spy = new AtomicInteger();

        @Override
        public void validate(InputGuardrailParams params) throws ValidationException {
            spy.incrementAndGet();
            throw new ValidationException("boom");
        }

        public int getSpy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class MyOkOutputGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger();

        @Override
        public void validate(OutputGuardrailParams params) throws ValidationException {
            spy.incrementAndGet();
        }

        public int getSpy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class MyKoOutputGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger();

        @Override
        public void validate(OutputGuardrailParams params) throws ValidationException {
            spy.incrementAndGet();
            throw new ValidationException("boom", false, null);
        }

        public int getSpy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class MyKoWithRetryOutputGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger();

        @Override
        public void validate(OutputGuardrailParams params) throws ValidationException {
            if (spy.incrementAndGet() == 1) {
                throw new ValidationException("KO", true, null);
            }
        }

        public int getSpy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class MyKoWithRepromprOutputGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger();

        @Override
        public void validate(OutputGuardrailParams params) throws ValidationException {
            if (spy.incrementAndGet() == 1) {
                throw new ValidationException("KO", true, "retry");
            }
        }

        public int getSpy() {
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
                    return new MessageWindowChatMemory.Builder().maxMessages(5).build();
                }
            };
        }
    }
}
