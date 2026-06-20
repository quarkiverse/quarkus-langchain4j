package io.quarkiverse.langchain4j.tests.scopes.invocation;

import static io.quarkiverse.langchain4j.runtime.LangChain4jUtil.chatMessageToText;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.chatscopes.InvocationScoped;
import io.quarkus.test.QuarkusUnitTest;

public class ChatMemoryTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MirrorChatModel.class));

    @ApplicationScoped
    public static class MirrorChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(new AiMessage(chatMessageToText(chatRequest.messages().get(0))))
                    .build();
        }
    }

    @RegisterAiService
    @InvocationScoped
    interface AiService {
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    @RegisterAiService
    @RequestScoped
    interface RequestScopedAiService {
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    @Inject
    AiService aiService;

    @Inject
    RequestScopedAiService requestScopedAiService;

    @Inject
    ChatMemoryStore chatMemoryStore;

    @Test
    @ActivateRequestContext
    public void testChatMemory() {
        requestScopedAiService.chat("123", "Hello");
        Assertions.assertTrue(chatMemoryStore.getMessages("123").size() > 0);
        aiService.chat("123", "Hello");
        Assertions.assertEquals(0, chatMemoryStore.getMessages("123").size());
    }

}
