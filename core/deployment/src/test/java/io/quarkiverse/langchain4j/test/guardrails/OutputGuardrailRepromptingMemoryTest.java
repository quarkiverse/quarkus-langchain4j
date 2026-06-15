package io.quarkiverse.langchain4j.test.guardrails;

import static io.quarkiverse.langchain4j.runtime.LangChain4jUtil.chatMessageToText;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class OutputGuardrailRepromptingMemoryTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class,
                            MyChatModel.class, MyChatModelSupplier.class, MyMemoryProviderSupplier.class,
                            RepromptOnceGuardrail.class));

    @Inject
    MyAiService aiService;

    @Inject
    RepromptOnceGuardrail guardrail;

    @Inject
    MyMemoryProviderSupplier memoryProviderSupplier;

    @Test
    @ActivateRequestContext
    void test() {
        guardrail.reset();

        String result = aiService.greet("mem1", "hello");

        // The return value should be the reprompted (validated) response
        assertThat(result).isEqualTo("VALIDATED");

        // The memory should also contain the validated response, not the first rejected one
        List<ChatMessage> messages = memoryProviderSupplier.getMemory("mem1").messages();
        ChatMessage lastMessage = messages.get(messages.size() - 1);
        assertThat(lastMessage).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) lastMessage).text())
                .as("Chat memory should contain the validated AI response, not the first rejected one")
                .isEqualTo("VALIDATED");
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    @SystemMessage("You are a helpful assistant.")
    public interface MyAiService {

        @OutputGuardrails(RepromptOnceGuardrail.class)
        String greet(@MemoryId String memoryId, @dev.langchain4j.service.UserMessage String message);
    }

    @ApplicationScoped
    public static class RepromptOnceGuardrail implements OutputGuardrail {

        private final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            if (callCount.incrementAndGet() == 1) {
                return reprompt("Response was not valid.", "Please reply with VALIDATED");
            }
            return success();
        }

        public void reset() {
            callCount.set(0);
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            ChatMessage last = request.messages().get(request.messages().size() - 1);
            if (last instanceof UserMessage && chatMessageToText(last).contains("VALIDATED")) {
                return ChatResponse.builder().aiMessage(AiMessage.from("VALIDATED")).build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("REJECTED")).build();
        }
    }

    @ApplicationScoped
    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        private final Map<String, ChatMemory> memories = new HashMap<>();

        @Override
        public ChatMemoryProvider get() {
            return memoryId -> memories.computeIfAbsent(memoryId.toString(),
                    k -> MessageWindowChatMemory.withMaxMessages(10));
        }

        public ChatMemory getMemory(String id) {
            return memories.get(id);
        }
    }
}
