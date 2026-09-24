package io.quarkiverse.langchain4j.chatscopes.passivation.test.shutdown;

import java.util.Collection;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.ChatMemoryRemover;
import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodImplementationSupport;
import io.quarkus.test.QuarkusUnitTest;

@Order(2)
public class ChatMemoryShutdownRestartTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(
                            AiService.class, FileChatMemoryStore.class, MirrorModelSupplier.class));

    @Inject
    AiService chat;

    @Inject
    ChatMemoryStore store;

    @Test
    void test() {
        ChatScope.activate(ChatMemoryShutdownTest.CHAT_MEMORY_SHUTDOWN_TEST);
        Collection<Object> ids = ChatMemoryRemover.getAllChatMemoryIds(chat);
        Assertions.assertEquals(1, ids.size());
        Object id = ids.iterator().next();
        Assertions.assertEquals(id, ChatMemoryShutdownTest.CHAT_MEMORY_SHUTDOWN_TEST
                + AiServiceMethodImplementationSupport.memoryIdSuffix(AiService.class.getName(), "chat"));
        Assertions.assertFalse(store.getMessages(id).isEmpty());
        ChatScope.end();
        Assertions.assertTrue(store.getMessages(id).isEmpty());

    }

}
