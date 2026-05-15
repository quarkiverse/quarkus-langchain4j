package io.quarkiverse.langchain4j.tests.scopes.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
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
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.Thinking;
import io.quarkiverse.langchain4j.chatscopes.ChatRoute;
import io.quarkiverse.langchain4j.chatscopes.ChatRouteConstants;
import io.quarkiverse.langchain4j.chatscopes.ChatRouteThinking;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.LocalChatRoutes;
import io.quarkiverse.langchain4j.runtime.aiservice.ThinkingEmitted;
import io.quarkus.test.QuarkusUnitTest;

public class ChatScopeThinkingForwarderTest {

    static final String THINKING_TEXT = "Let me think step by step about this problem.";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(
                            ThinkingAssistant.class, ThinkingModelSupplier.class, ThinkingRoute.class));

    public static class ThinkingModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest chatRequest) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.builder().text("4").thinking(THINKING_TEXT).build())
                            .build();
                }
            };
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = ThinkingModelSupplier.class)
    @ChatScoped
    interface ThinkingAssistant {
        String solve(@UserMessage String input);

        @Thinking
        static void onThinking(ThinkingEmitted event) {
            ChatRouteThinking.forward(event);
        }
    }

    @ApplicationScoped
    public static class ThinkingRoute {

        @Inject
        ThinkingAssistant assistant;

        @ChatRoute("solve")
        public String solve(@UserMessage String input) {
            return assistant.solve(input);
        }
    }

    @Inject
    LocalChatRoutes.Client localChatRouter;

    @Test
    public void thinkingHandlerForwardsToResponseChannel() throws Exception {
        Map<String, List<Object>> events = new HashMap<>();
        Semaphore semaphore = new Semaphore(0);
        try (LocalChatRoutes.Session session = localChatRouter.builder().defaultHandler((event, data) -> {
            events.computeIfAbsent(event, k -> new ArrayList<>()).add(data);
            semaphore.release();
        }).connect("solve")) {
            session.chat("What is 2+2?");
            semaphore.tryAcquire(2, 500, TimeUnit.MILLISECONDS);
        }

        List<Object> thinkingEvents = events.get(ChatRouteConstants.THINKING);
        Assertions.assertNotNull(thinkingEvents, "Expected at least one Thinking event, got: " + events.keySet());
        Assertions.assertEquals(1, thinkingEvents.size());
        Assertions.assertEquals(THINKING_TEXT, thinkingEvents.get(0));
    }
}
