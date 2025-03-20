package io.quarkiverse.langchain4j.test.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.Supplier;

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
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.InputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkus.test.QuarkusUnitTest;

public class InputGuardrailRewritingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, MessageTruncatingGuardrail.class, EchoChatModel.class,
                            MyChatModelSupplier.class, MyMemoryProviderSupplier.class));

    @Inject
    MyAiService aiService;

    @Test
    @ActivateRequestContext
    void testRewriting() {
        assertEquals(MessageTruncatingGuardrail.MAX_LENGTH, aiService.test("first prompt", "second prompt").length());
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @UserMessage("Given {first} and {second} do something")
        @InputGuardrails(MessageTruncatingGuardrail.class)
        String test(String first, String second);

    }

    @RequestScoped
    public static class MessageTruncatingGuardrail implements InputGuardrail {

        static final int MAX_LENGTH = 20;

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage um) {
            String text = um.singleText();
            return successWith(text.substring(0, MAX_LENGTH));
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatLanguageModel> {

        @Override
        public ChatLanguageModel get() {
            return new EchoChatModel();
        }
    }

    public static class EchoChatModel implements ChatLanguageModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder()
                    .aiMessage(
                            new AiMessage(((dev.langchain4j.data.message.UserMessage) request.messages().get(0)).singleText()))
                    .build();
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
