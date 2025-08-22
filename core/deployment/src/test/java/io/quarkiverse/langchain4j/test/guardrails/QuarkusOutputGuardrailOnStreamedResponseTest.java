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
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailAccumulator;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrails;
import io.quarkiverse.langchain4j.guardrails.OutputTokenAccumulator;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * @deprecated These tests will go away once the Quarkus-specific guardrail implementation has been fully removed
 */
@Deprecated(forRemoval = true)
public class QuarkusOutputGuardrailOnStreamedResponseTest {

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
    void testThatOutputGuardrailsAreInvoked() {
        assertThat(Arc.container().requestContext().isActive()).isFalse();
        Arc.container().requestContext().activate();
        try {
            assertThat(okGuardrail.spy()).isEqualTo(0);
            aiService.hiUsingDefaultAccumulator("1").collect().asList().await().indefinitely();
            assertThat(okGuardrail.spy()).isEqualTo(1);
            aiService.hiUsingDefaultAccumulator("2").collect().asList().await().indefinitely();
            assertThat(okGuardrail.spy()).isEqualTo(2);
        } finally {
            Arc.container().requestContext().deactivate();
        }

        Arc.container().requestContext().activate();
        try {
            // New request scope - the value should be back to 0
            assertThat(okGuardrail.spy()).isEqualTo(0);
            aiService.hiUsingDefaultAccumulator("1").collect().asList().await().indefinitely();
            assertThat(okGuardrail.spy()).isEqualTo(1);
            aiService.hiUsingDefaultAccumulator("1").collect().asList().await().indefinitely();
            assertThat(okGuardrail.spy()).isEqualTo(2);
        } finally {
            Arc.container().requestContext().deactivate();
        }

        assertThat(Arc.container().requestContext().isActive()).isFalse();

        Arc.container().requestContext().activate();
        try {
            assertThat(okGuardrail.spy()).isEqualTo(0);
            aiService.hiUsingPassThroughAccumulator("1").collect().asList().await().indefinitely();
            assertThat(okGuardrail.spy()).isEqualTo(3); // 3 chunks
            aiService.hiUsingPassThroughAccumulator("2").collect().asList().await().indefinitely();
            assertThat(okGuardrail.spy()).isEqualTo(6); // 3+3 chunks
        } finally {
            Arc.container().requestContext().deactivate();
        }

        Arc.container().requestContext().activate();
        try {
            // New request scope - the value should be back to 0
            assertThat(okGuardrail.spy()).isEqualTo(0);
            aiService.hiUsingPassThroughAccumulator("1").collect().asList().await().indefinitely();
            assertThat(okGuardrail.spy()).isEqualTo(3);
            aiService.hiUsingPassThroughAccumulator("1").collect().asList().await().indefinitely();
            assertThat(okGuardrail.spy()).isEqualTo(6);
        } finally {
            Arc.container().requestContext().deactivate();
        }
    }

    @Test
    @ActivateRequestContext
    void testThatGuardrailCanThrowValidationException() {
        assertThat(koGuardrail.spy()).isEqualTo(0);
        assertThatThrownBy(() -> aiService.koUsingDefaultAccumulator("1").collect().asList().await().indefinitely())
                .hasCauseExactlyInstanceOf(ValidationException.class);
        assertThat(koGuardrail.spy()).isEqualTo(1);
        assertThatThrownBy(() -> aiService.koUsingDefaultAccumulator("1").collect().asList().await().indefinitely())
                .hasCauseExactlyInstanceOf(ValidationException.class);
        assertThat(koGuardrail.spy()).isEqualTo(2);
    }

    @Test
    @ActivateRequestContext
    void testThatGuardrailCanThrowValidationExceptionWhenUsingPassThroughAccumulator() {
        assertThat(koGuardrail.spy()).isEqualTo(0);
        assertThatThrownBy(() -> aiService.koUsingPassThroughAccumulator("1").collect().asList().await().indefinitely())
                .hasCauseExactlyInstanceOf(ValidationException.class);
        assertThat(koGuardrail.spy()).isEqualTo(2); // First chunk is ok, the second one fails.
        assertThatThrownBy(() -> aiService.koUsingPassThroughAccumulator("1").collect().asList().await().indefinitely())
                .hasCauseExactlyInstanceOf(ValidationException.class);
        assertThat(koGuardrail.spy()).isEqualTo(4);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @UserMessage("Say Hi!")
        @OutputGuardrails(OKGuardrail.class)
        Multi<String> hiUsingDefaultAccumulator(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOGuardrail.class)
        Multi<String> koUsingDefaultAccumulator(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(OKGuardrail.class)
        @OutputGuardrailAccumulator(PassThroughAccumulator.class)
        Multi<String> hiUsingPassThroughAccumulator(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOGuardrail.class)
        @OutputGuardrailAccumulator(PassThroughAccumulator.class)
        Multi<String> koUsingPassThroughAccumulator(@MemoryId String mem);

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

    @ApplicationScoped
    public static class PassThroughAccumulator implements OutputTokenAccumulator {

        @Override
        public Multi<String> accumulate(Multi<String> tokens) {
            return tokens;
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
