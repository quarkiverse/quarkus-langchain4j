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
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

public class GuardrailTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, OKGuardrail.class, KOGuardrail.class,
                            MyChatModel.class, MyChatModelSupplier.class, MyMemoryProviderSupplier.class));

    @Inject
    MyAiService aiService;

    @Inject
    OKGuardrail okGuardrail;
    @Inject
    KOGuardrail koGuardrail;

    @Test
    void testThatGuardrailAreInvoked() {
        assertThat(Arc.container().requestContext().isActive()).isFalse();
        Arc.container().requestContext().activate();
        assertThat(okGuardrail.spy()).isEqualTo(0);
        aiService.hi("1");
        assertThat(okGuardrail.spy()).isEqualTo(1);
        aiService.hi("2");
        assertThat(okGuardrail.spy()).isEqualTo(2);
        Arc.container().requestContext().deactivate();

        Arc.container().requestContext().activate();
        // New request scope - the value should be back to 0
        assertThat(okGuardrail.spy()).isEqualTo(0);
        aiService.hi("1");
        assertThat(okGuardrail.spy()).isEqualTo(1);
        aiService.hi("2");
        assertThat(okGuardrail.spy()).isEqualTo(2);
    }

    @Test
    @ActivateRequestContext
    void testThatGuardrailCanThrowValidationException() {
        assertThat(koGuardrail.spy()).isEqualTo(0);
        assertThatThrownBy(() -> aiService.ko("1"))
                .hasCauseExactlyInstanceOf(OutputGuardrail.ValidationException.class);
        assertThat(koGuardrail.spy()).isEqualTo(1);
        assertThatThrownBy(() -> aiService.ko("1"))
                .hasCauseExactlyInstanceOf(OutputGuardrail.ValidationException.class);
        assertThat(koGuardrail.spy()).isEqualTo(2);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @UserMessage("Say Hi!")
        @OutputGuardrails(OKGuardrail.class)
        String hi(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOGuardrail.class)
        String ko(@MemoryId String mem);

    }

    @RequestScoped
    public static class OKGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public void validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
        }

        public int spy() {
            return spy.get();
        }
    }

    @ApplicationScoped
    public static class KOGuardrail implements OutputGuardrail {

        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public void validate(AiMessage responseFromLLM) throws ValidationException {
            spy.incrementAndGet();
            throw new ValidationException("KO", false, null);
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
