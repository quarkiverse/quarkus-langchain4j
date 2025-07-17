package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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

public class OutputGuardrailChainOnTokenStreamResponseTest extends TokenStreamExecutor {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    MyAiService aiService;

    @Inject
    FirstGuardrail firstGuardrail;

    @Inject
    SecondGuardrail secondGuardrail;

    @Inject
    FailingGuardrail failingGuardrail;

    @Test
    @ActivateRequestContext
    void testThatGuardrailChainsAreInvoked() throws InterruptedException {
        execute(() -> aiService.firstOneTwo("1", "foo"));
        assertThat(firstGuardrail.spy()).isEqualTo(1);
        assertThat(secondGuardrail.spy()).isEqualTo(1);
        assertThat(firstGuardrail.lastAccess()).isLessThan(secondGuardrail.lastAccess());
    }

    @Test
    @ActivateRequestContext
    void testThatGuardrailOrderIsCorrect() throws InterruptedException {
        execute(() -> aiService.twoAndFirst("1", "foo"));
        assertThat(firstGuardrail.spy()).isEqualTo(1);
        assertThat(secondGuardrail.spy()).isEqualTo(1);
        assertThat(secondGuardrail.lastAccess()).isLessThan(firstGuardrail.lastAccess());
    }

    @Test
    @ActivateRequestContext
    void testThatRetryRestartTheChain() throws InterruptedException {
        execute(() -> aiService.failingFirstTwo("1", "foo"));
        assertThat(firstGuardrail.spy()).isEqualTo(2);
        assertThat(secondGuardrail.spy()).isEqualTo(1);
        assertThat(failingGuardrail.spy()).isEqualTo(2);
        assertThat(firstGuardrail.lastAccess()).isLessThan(secondGuardrail.lastAccess());
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {
        @OutputGuardrails({ FirstGuardrail.class, SecondGuardrail.class })
        TokenStream firstOneTwo(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({ SecondGuardrail.class, FirstGuardrail.class })
        TokenStream twoAndFirst(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({ FirstGuardrail.class, FailingGuardrail.class, SecondGuardrail.class })
        TokenStream failingFirstTwo(@MemoryId String mem, @UserMessage String message);
    }

    @RequestScoped
    public static class FirstGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);
        AtomicLong lastAccess = new AtomicLong();

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            lastAccess.set(System.nanoTime());
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore me
            }
            return success();
        }

        public int spy() {
            return spy.get();
        }

        public long lastAccess() {
            return lastAccess.get();
        }
    }

    @RequestScoped
    public static class SecondGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);
        volatile AtomicLong lastAccess = new AtomicLong();

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            lastAccess.set(System.nanoTime());
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore me
            }
            return success();
        }

        public int spy() {
            return spy.get();
        }

        public long lastAccess() {
            return lastAccess.get();
        }
    }

    @RequestScoped
    public static class FailingGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            if (spy.incrementAndGet() == 1) {
                return reprompt("Retry", "Retry");
            }
            return success();
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
