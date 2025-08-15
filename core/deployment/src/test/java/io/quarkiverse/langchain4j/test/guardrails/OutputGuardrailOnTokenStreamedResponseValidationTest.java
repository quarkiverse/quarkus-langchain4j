package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class OutputGuardrailOnTokenStreamedResponseValidationTest extends TokenStreamExecutor {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyStreamedChatModel.class));

    @Inject
    MyAiService aiService;

    @Inject
    RetryingGuardrail retry;

    @Inject
    RewritingGuardrail rewriting;

    @Inject
    RetryingButFailGuardrail retryFail;

    @Inject
    KOFatalGuardrail fatal;

    @Test
    @ActivateRequestContext
    void testOk() throws InterruptedException {
        execute(() -> aiService.ok("1"));
    }

    @Test
    @ActivateRequestContext
    void testKO() {
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> execute(() -> aiService.ko("2")))
                .withMessageContaining("KO");
    }

    @Test
    @ActivateRequestContext
    void testRetryOk() throws InterruptedException {
        execute(() -> aiService.retry("3"));
        assertThat(retry.spy()).isEqualTo(2);
    }

    @Test
    @ActivateRequestContext
    void testRetryFail() {
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> execute(() -> aiService.retryButFail("4")))
                .withMessageContaining("maximum number of retries");
        assertThat(retryFail.spy()).isEqualTo(3);
    }

    @Test
    @ActivateRequestContext
    void testFatalException() {
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> execute(() -> aiService.fatal("5")))
                .withMessageContaining("Fatal");
        assertThat(fatal.spy()).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void testRewritingWhileStreaming() throws InterruptedException {
        assertThat(execute(() -> aiService.rewriting("1"))).isEqualTo("Hi!   World! ,1");
        assertThat(rewriting.spy()).isEqualTo(1);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @UserMessage("Say Hi!")
        @OutputGuardrails(OKGuardrail.class)
        TokenStream ok(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOGuardrail.class)
        TokenStream ko(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(RetryingGuardrail.class)
        TokenStream retry(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(RetryingButFailGuardrail.class)
        TokenStream retryButFail(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOFatalGuardrail.class)
        TokenStream fatal(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails({ RewritingGuardrail.class })
        TokenStream rewriting(@MemoryId String mem);
    }

    @RequestScoped
    public static class OKGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            return success();
        }

        public int spy() {
            return spy.get();
        }
    }

    @ApplicationScoped
    public static class KOGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            return failure("KO");
        }

        public int spy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class RetryingGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            int v = spy.incrementAndGet();
            if (v >= 2) {
                return OutputGuardrailResult.success();
            }
            return retry("KO");
        }

        public int spy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class RetryingButFailGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            return retry("KO");
        }

        public int spy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class KOFatalGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            throw new IllegalArgumentException("Fatal");
        }

        public int spy() {
            return spy.get();
        }
    }

    @RequestScoped
    public static class RewritingGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            String text = responseFromLLM.text();
            return successWith(text + ",1");
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class MyChatModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return new MyStreamedChatModel();
        }
    }

    public static class MyStreamedChatModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            handler.onPartialResponse("Hi!");
            handler.onPartialResponse(" ");
            handler.onPartialResponse("World!");
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("")).build());
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return MessageWindowChatMemory.withMaxMessages(10);
                }
            };
        }
    }
}
