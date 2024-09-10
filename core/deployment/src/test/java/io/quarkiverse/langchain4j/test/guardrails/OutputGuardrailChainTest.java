package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkus.test.QuarkusUnitTest;

public class OutputGuardrailChainTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class,
                            MyChatModel.class, MyChatModelSupplier.class, MyMemoryProviderSupplier.class));

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
    void testThatGuardrailChainsAreInvoked() {
        aiService.firstOneTwo("1", "foo");
        assertThat(firstGuardrail.spy()).isEqualTo(1);
        assertThat(secondGuardrail.spy()).isEqualTo(1);
        assertThat(firstGuardrail.lastAccess()).isLessThan(secondGuardrail.lastAccess());
    }

    @Test
    @ActivateRequestContext
    void testThatGuardrailOrderIsCorrect() {
        aiService.twoAndFirst("1", "foo");
        assertThat(firstGuardrail.spy()).isEqualTo(1);
        assertThat(secondGuardrail.spy()).isEqualTo(1);
        assertThat(secondGuardrail.lastAccess()).isLessThan(firstGuardrail.lastAccess());
    }

    @Test
    @ActivateRequestContext
    void testThatRetryRestartTheChain() {
        aiService.failingFirstTwo("1", "foo");
        assertThat(firstGuardrail.spy()).isEqualTo(2);
        assertThat(secondGuardrail.spy()).isEqualTo(1);
        assertThat(failingGuardrail.spy()).isEqualTo(2);
        assertThat(firstGuardrail.lastAccess()).isLessThan(secondGuardrail.lastAccess());
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @OutputGuardrails({ FirstGuardrail.class, SecondGuardrail.class })
        String firstOneTwo(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({ SecondGuardrail.class, FirstGuardrail.class })
        String twoAndFirst(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({ FirstGuardrail.class, FailingGuardrail.class, SecondGuardrail.class })
        String failingFirstTwo(@MemoryId String mem, @UserMessage String message);

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
