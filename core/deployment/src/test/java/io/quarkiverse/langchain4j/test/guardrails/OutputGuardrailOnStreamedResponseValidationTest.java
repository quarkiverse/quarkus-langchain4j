package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import dev.langchain4j.guardrail.GuardrailException;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailAccumulator;
import io.quarkiverse.langchain4j.guardrails.OutputTokenAccumulator;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class OutputGuardrailOnStreamedResponseValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyStreamedChatModel.class));

    @Inject
    MyAiService aiService;

    @Inject
    OKGuardrail okGuardrail;

    @Inject
    RewritingGuardrail rewriting;

    @Test
    @ActivateRequestContext
    void testOk() {
        aiService.ok("1").collect().asList().await().indefinitely();
        assertThat(okGuardrail.spy()).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void testOkWithPassThroughAccumulator() {
        aiService.okWithPassThroughAccumulator("1").collect().asList().await().indefinitely();
        assertThat(okGuardrail.spy()).isEqualTo(3);
    }

    @Test
    @ActivateRequestContext
    void testKO() {
        assertThatThrownBy(() -> aiService.ko("2").collect().asList().await().indefinitely())
                .isInstanceOf(GuardrailException.class)
                .hasMessageContaining("KO");
    }

    @Test
    @ActivateRequestContext
    void testKOWithPassThroughAccumulator() {
        assertThatThrownBy(() -> aiService.koWithPassThroughAccumulator("2").collect().asList().await().indefinitely())
                .isInstanceOf(GuardrailException.class)
                .hasMessageContaining("KO");
    }

    @Inject
    RetryingGuardrail retry;

    @Test
    @ActivateRequestContext
    void testRetryOk() {
        assertThat(aiService.retry("3").collect().asList().await().indefinitely())
                .singleElement()
                .isEqualTo("Hi! World!");
        assertThat(retry.spy()).isEqualTo(2);
    }

    @Test
    @ActivateRequestContext
    void testRetryOkWithPassThroughAccumulator() {
        assertThat(aiService.retryWithPassThroughAccumulator("3").collect().asList().await().indefinitely())
                .containsExactly("Hi!", " ", "World!");
        assertThat(retry.spy()).isEqualTo(4); // "Hi!", "Hi!" (retry), " ", "World!"
    }

    @Inject
    RetryingButFailGuardrail retryFail;

    @Test
    @ActivateRequestContext
    void testRetryFail() {
        assertThatThrownBy(() -> aiService.retryButFail("4").collect().asList().await().indefinitely())
                .isInstanceOf(GuardrailException.class)
                .hasMessageContaining("maximum number of retries");
        assertThat(retryFail.spy()).isEqualTo(4);
    }

    @Inject
    PassThroughAccumulator accumulator;

    @Test
    @ActivateRequestContext
    void testRetryFailWithPassThroughAccumulator() {
        assertThatThrownBy(
                () -> aiService.retryButFailWithPassThroughAccumulator("4").collect().asList().await().indefinitely())
                .isInstanceOf(GuardrailException.class)
                .hasMessageContaining("maximum number of retries");
        assertThat(retryFail.spy()).isEqualTo(4);
        assertThat(accumulator.spy()).isEqualTo(4);
    }

    @Inject
    KOFatalGuardrail fatal;

    @Test
    @ActivateRequestContext
    void testFatalException() {
        assertThatThrownBy(() -> aiService.fatal("5").collect().asList().await().indefinitely())
                .isInstanceOf(GuardrailException.class)
                .hasMessageContaining("Fatal");
        assertThat(fatal.spy()).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void testFatalExceptionWithPassThroughAccumulator() {
        assertThatThrownBy(() -> aiService.fatalWithPassThroughAccumulator("5").collect().asList().await().indefinitely())
                .isInstanceOf(GuardrailException.class)
                .hasMessageContaining("Fatal");
        assertThat(fatal.spy()).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void rewritingWhileStreaming() throws InterruptedException {
        assertThat(aiService.rewriting("1").collect().asList().await().indefinitely())
                .singleElement()
                .isEqualTo("Hi! World!,1");
        assertThat(rewriting.spy()).isEqualTo(1);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @UserMessage("Say Hi!")
        @OutputGuardrails(OKGuardrail.class)
        Multi<String> ok(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOGuardrail.class)
        Multi<String> ko(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(RetryingGuardrail.class)
        Multi<String> retry(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(RetryingButFailGuardrail.class)
        Multi<String> retryButFail(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOFatalGuardrail.class)
        Multi<String> fatal(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(OKGuardrail.class)
        @OutputGuardrailAccumulator(PassThroughAccumulator.class)
        Multi<String> okWithPassThroughAccumulator(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOGuardrail.class)
        @OutputGuardrailAccumulator(PassThroughAccumulator.class)
        Multi<String> koWithPassThroughAccumulator(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(RetryingGuardrail.class)
        @OutputGuardrailAccumulator(PassThroughAccumulator.class)
        Multi<String> retryWithPassThroughAccumulator(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(RetryingButFailGuardrail.class)
        @OutputGuardrailAccumulator(PassThroughAccumulator.class)
        Multi<String> retryButFailWithPassThroughAccumulator(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOFatalGuardrail.class)
        @OutputGuardrailAccumulator(PassThroughAccumulator.class)
        Multi<String> fatalWithPassThroughAccumulator(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails({ RewritingGuardrail.class })
        Multi<String> rewriting(@MemoryId String mem);
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
        AtomicInteger spy = new AtomicInteger(0);

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

    @RequestScoped
    public static class PassThroughAccumulator implements OutputTokenAccumulator {

        AtomicInteger spy = new AtomicInteger();

        public int spy() {
            return spy.get();
        }

        @Override
        public Multi<String> accumulate(Multi<String> tokens) {
            return tokens
                    .onSubscription().invoke(() -> spy.incrementAndGet());
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
