package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

public class OutputGuardrailOnTokenStreamedResponseTest extends TokenStreamExecutor {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, OKGuardrail.class, KOGuardrail.class,
                            MyChatModelSupplier.class, MyMemoryProviderSupplier.class, ValidationException.class));

    @Inject
    MyAiService aiService;

    @Inject
    OKGuardrail okGuardrail;

    @Inject
    KOGuardrail koGuardrail;

    @Test
    void testThatOutputGuardrailsAreInvoked() throws InterruptedException {
        assertThat(Arc.container().requestContext().isActive()).isFalse();
        Arc.container().requestContext().activate();
        try {
            assertThat(okGuardrail.spy()).isEqualTo(0);
            execute(() -> aiService.hi("1"));
            assertThat(okGuardrail.spy()).isEqualTo(1);
            execute(() -> aiService.hi("2"));
            assertThat(okGuardrail.spy()).isEqualTo(2);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            Arc.container().requestContext().deactivate();
        }

        Arc.container().requestContext().activate();
        try {
            // New request scope - the value should be back to 0
            assertThat(okGuardrail.spy()).isEqualTo(0);
            execute(() -> aiService.hi("1"));
            assertThat(okGuardrail.spy()).isEqualTo(1);
            execute(() -> aiService.hi("1"));
            assertThat(okGuardrail.spy()).isEqualTo(2);
        } finally {
            Arc.container().requestContext().deactivate();
        }

        assertThat(Arc.container().requestContext().isActive()).isFalse();
    }

    @Test
    @ActivateRequestContext
    void testThatGuardrailCanThrowValidationException() {
        assertThat(koGuardrail.spy()).isEqualTo(0);
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> execute(() -> aiService.ko("1")))
                .havingCause()
                .isInstanceOf(ValidationException.class);
        assertThat(koGuardrail.spy()).isEqualTo(1);
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> execute(() -> aiService.ko("1")))
                .havingCause()
                .isInstanceOf(ValidationException.class);
        assertThat(koGuardrail.spy()).isEqualTo(2);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @UserMessage("Say Hi!")
        @OutputGuardrails(OKGuardrail.class)
        TokenStream hi(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOGuardrail.class)
        TokenStream ko(@MemoryId String mem);

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

    @RequestScoped
    public static class KOGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            if (responseFromLLM.text().length() > 3) { // Accumulated response.
                return failure("KO", new ValidationException("KO"));
            } else { // Chunk, do not fail on the first chunk
                if (responseFromLLM.text().contains("Hi!")) {
                    return success();
                } else {
                    return failure("KO", new ValidationException("KO"));
                }
            }
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
                    return new NoopChatMemory();
                }
            };
        }
    }
}
