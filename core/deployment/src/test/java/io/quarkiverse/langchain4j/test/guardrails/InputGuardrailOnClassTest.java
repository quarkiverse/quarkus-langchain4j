package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
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
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.InputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkus.test.QuarkusUnitTest;

public class InputGuardrailOnClassTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, OKGuardrail.class,
                            MyChatModel.class, MyChatModelSupplier.class, MyMemoryProviderSupplier.class));

    @Inject
    MyAiService aiService;

    @Inject
    OKGuardrail okGuardrail;

    @Test
    @ActivateRequestContext
    void testThatGuardrailsFromTheClassAreInvoked() {
        assertThat(okGuardrail.spy()).isEqualTo(0);
        aiService.hi("1");
        assertThat(okGuardrail.spy()).isEqualTo(1);
        aiService.hi("2");
        assertThat(okGuardrail.spy()).isEqualTo(2);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    @InputGuardrails(OKGuardrail.class)
    public interface MyAiService {

        @UserMessage("Say Hi!")
        String hi(@MemoryId String mem);

    }

    @ApplicationScoped
    public static class OKGuardrail implements InputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage ignored) {
            spy.incrementAndGet();
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
