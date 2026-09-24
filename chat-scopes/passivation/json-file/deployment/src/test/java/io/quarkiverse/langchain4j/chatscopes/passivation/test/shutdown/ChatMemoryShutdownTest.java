package io.quarkiverse.langchain4j.chatscopes.passivation.test.shutdown;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeManagedContext;
import io.quarkus.test.QuarkusUnitTest;

@Order(1)
public class ChatMemoryShutdownTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(
                            AiService.class, FileChatMemoryStore.class, MirrorModelSupplier.class));
    public static final String CHAT_MEMORY_SHUTDOWN_TEST = "chat-memory-shutdown-test";

    @Inject
    AiService chat;

    @Inject
    FileChatMemoryStore store;

    @Test
    void test() {
        store.clearDisk(); // just in case there is junk there from a failed build
        ChatScopeManagedContext.INSTANCE.begin(CHAT_MEMORY_SHUTDOWN_TEST, null);
        ChatScope.end(); // we make sure this is a clean test
        ChatScopeManagedContext.INSTANCE.begin(CHAT_MEMORY_SHUTDOWN_TEST, null);
        chat.chat("Hello");
        ChatScope.deactivate();
    }

}
