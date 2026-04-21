package io.quarkiverse.langchain4j.tests.routes;

import static io.quarkiverse.langchain4j.runtime.LangChain4jUtil.chatMessageToText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.ChatMemoryRemover;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.chatscopes.ChatRoute;
import io.quarkiverse.langchain4j.chatscopes.ChatRoutes;
import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.ChatScopeMemory;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.LocalChatRoutes;
import io.quarkiverse.langchain4j.chatscopes.LocalChatRoutes.Session;
import io.quarkus.test.QuarkusUnitTest;

public class WipeTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(AiService.class, MirrorModelSupplier.class,
                            CustomChatMemoryStore.class, RouteBean.class));

    public static class MirrorModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest chatRequest) {
                    return ChatResponse.builder()
                            .aiMessage(new AiMessage(chatMessageToText(chatRequest.messages().get(0))))
                            .build();
                }
            };
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = MirrorModelSupplier.class)
    @ChatScoped
    interface AiService {
        String chat(@UserMessage String userMessage);
    }

    @ApplicationScoped
    public static class CustomChatMemoryStore implements ChatMemoryStore {

        private final Map<Object, List<ChatMessage>> messagesByMemoryId = new ConcurrentHashMap<>();

        public Map<Object, List<ChatMessage>> all() {
            return messagesByMemoryId;
        }

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return messagesByMemoryId.computeIfAbsent(memoryId, ignored -> new ArrayList<>());
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            messagesByMemoryId.put(memoryId, messages);
        }

        @Override
        public void deleteMessages(Object memoryId) {
            messagesByMemoryId.remove(memoryId);
        }
    }

    @ChatScoped
    public static class RouteBean {

        @Inject
        AiService aiService;

        @Inject
        CustomChatMemoryStore customChatMemoryStore;

        int counter = 0;

        @ChatRoute("chat")
        public String chat(String nextRoute) {
            aiService.chat("Hello");
            Collection<Object> memoryIds = ChatMemoryRemover.getAllChatMemoryIds(aiService);
            Assertions.assertEquals(1, memoryIds.size());
            if (nextRoute != null) {
                ChatRoutes.current(nextRoute);
            }

            return "" + customChatMemoryStore.getMessages(memoryIds.iterator().next()).size();
        }

        @ChatRoute("schedule-wipe")
        public String scheduleWipe() {
            ChatScopeMemory.scheduleWipe();
            ChatRoutes.current("assert-wiped");
            return "wipeScheduled";
        }

        @ChatRoute("abort-wipe")
        public String abortWipe() {
            ChatScopeMemory.scheduleWipe();
            ChatScopeMemory.abortWipe();
            ChatRoutes.current("chat");
            return "abort-wipe";
        }

        @ChatRoute("assert-wiped")
        public String assertWiped() {
            Assertions.assertEquals(0, customChatMemoryStore.all().size());
            ChatRoutes.current("chat");
            return "assert-wiped";
        }

        @ChatRoute("parent-schedule-wipe-and-push")
        public String parentScheduleWipeAndPush() {
            ChatScopeMemory.scheduleWipe();
            ChatScope.push("child-chat-once");
            return "parent-wipe-scheduled";
        }

        @ChatRoute("child-chat-once")
        public String childChatOnce() {
            aiService.chat("Hello");
            Collection<Object> memoryIds = ChatMemoryRemover.getAllChatMemoryIds(aiService);
            // Parent memory was wiped at end of the previous turn; only child's key should remain
            Assertions.assertEquals(1, customChatMemoryStore.all().size());
            return "child-messages:" + customChatMemoryStore.getMessages(memoryIds.iterator().next()).size();
        }
    }

    @Inject
    LocalChatRoutes.Client localClient;

    @Test
    public void testWipe() throws Exception {
        AtomicReference<String> result = new AtomicReference<>();

        Semaphore semaphore = new Semaphore(0);
        Session session = localClient.builder()
                .messageHandler((msg) -> {
                    result.set(msg);
                    semaphore.release();

                })
                .connect("chat");

        session.chat();
        semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
        Assertions.assertEquals("2", result.get());
        session.chat(Map.of("nextRoute", "schedule-wipe"));
        semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
        Assertions.assertEquals("4", result.get());
        session.chat();
        semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
        Assertions.assertEquals("wipeScheduled", result.get());
        session.chat();
        semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
        Assertions.assertEquals("assert-wiped", result.get());

        session.chat(Map.of("nextRoute", "abort-wipe"));
        semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
        Assertions.assertEquals("2", result.get());
        session.chat();
        semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
        Assertions.assertEquals("abort-wipe", result.get());
        session.chat();
        semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
        Assertions.assertEquals("4", result.get());

        session.close();
    }

    @Test
    public void testParentWipeClearsParentButNotChild() throws Exception {
        AtomicReference<String> result = new AtomicReference<>();

        Semaphore semaphore = new Semaphore(0);
        Session session = localClient.builder()
                .messageHandler((msg) -> {
                    result.set(msg);
                    semaphore.release();

                })
                .connect("chat");

        // Build up 4 messages in parent scope memory
        session.chat();
        semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
        Assertions.assertEquals("2", result.get());
        session.chat(Map.of("nextRoute", "parent-schedule-wipe-and-push"));
        semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
        Assertions.assertEquals("4", result.get());

        // Schedule wipe on parent and push to child scope.
        // After this turn, executeScheduledWipes() fires:
        //   current=child (no wipe scheduled) → skipped
        //   parent (wipe scheduled) → parent memory cleared
        session.chat();
        semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
        Assertions.assertEquals("parent-wipe-scheduled", result.get());

        // Child scope: parent memory is gone, child's own messages are intact
        session.chat();
        semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
        Assertions.assertEquals("child-messages:2", result.get());

        session.close();
    }

}
