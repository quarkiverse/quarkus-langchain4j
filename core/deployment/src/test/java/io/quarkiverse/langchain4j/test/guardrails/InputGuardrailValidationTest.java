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
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.InputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.GuardrailException;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class InputGuardrailValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, OKGuardrail.class, KOGuardrail.class,
                            MyChatModel.class, MyStreamingChatModel.class, MyChatModelSupplier.class,
                            MyMemoryProviderSupplier.class, ValidationException.class));

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

    @Test
    @ActivateRequestContext
    void testOkMulti() {
        List<String> strings = aiService.okMulti("1").log()
                .collect().asList().await().indefinitely();

        assertThat(String.join(" ", strings)).isEqualTo("Streaming hi !");
    }

    @Test
    @ActivateRequestContext
    void testKoMulti() {
        assertThatThrownBy(() -> aiService.koMulti("2").subscribe().asIterable())
                .isInstanceOf(GuardrailException.class)
                .hasMessageContaining("KO");
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
    void testMemory() {
        aiService.test("1", "foo");
        aiService.test("1", "bar");
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, streamingChatLanguageModelSupplier = MyStreamingChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @UserMessage("Say Hi!")
        @InputGuardrails(OKGuardrail.class)
        String ok(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @InputGuardrails(KOGuardrail.class)
        String ko(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @InputGuardrails(OKGuardrail.class)
        Multi<String> okMulti(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @InputGuardrails(KOGuardrail.class)
        Multi<String> koMulti(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @InputGuardrails(KOFatalGuardrail.class)
        String fatal(@MemoryId String mem);

        @InputGuardrails(MemoryCheck.class)
        String test(@MemoryId String name, @UserMessage String message);
    }

    @RequestScoped
    public static class OKGuardrail implements InputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage um) {
            spy.incrementAndGet();
            return success();
        }

        public int spy() {
            return spy.get();
        }
    }

    @ApplicationScoped
    public static class KOGuardrail implements InputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage um) {
            spy.incrementAndGet();
            return failure("KO");
        }

        public int spy() {
            return spy.get();
        }
    }

    @ApplicationScoped
    public static class KOFatalGuardrail implements InputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage um) {
            spy.incrementAndGet();
            throw new IllegalArgumentException("Fatal");
        }

        public int spy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class MemoryCheck implements InputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public InputGuardrailResult validate(InputGuardrailParams params) {
            spy.incrementAndGet();
            if (params.memory().messages().isEmpty()) {
                assertThat(params.userMessage().singleText()).isEqualTo("foo");
            }
            if (params.memory().messages().size() == 2) {
                assertThat(params.memory().messages().get(0).text()).isEqualTo("foo");
                assertThat(params.memory().messages().get(1).text()).isEqualTo("Hi!");
                assertThat(params.userMessage().singleText()).isEqualTo("bar");
            }
            return success();
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

    public static class MyStreamingChatModelSupplier implements Supplier<StreamingChatLanguageModel> {

        @Override
        public StreamingChatLanguageModel get() {
            return new MyStreamingChatModel();
        }
    }

    public static class MyChatModel implements ChatLanguageModel {

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return new Response<>(new AiMessage("Hi!"));
        }
    }

    public static class MyStreamingChatModel implements StreamingChatLanguageModel {

        @Override
        public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
            handler.onNext("Streaming hi");
            handler.onNext("!");
            handler.onComplete(Response.from(AiMessage.from("")));
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
