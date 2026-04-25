package io.quarkiverse.langchain4j.test;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Regression test for <a href="https://github.com/quarkiverse/quarkus-langchain4j/issues/2381">issue
 * #2381</a>.
 * <p>
 * Verifies that programmatic {@code AiServices.builder()} with a single shared
 * {@link MessageWindowChatMemory} (no {@link MemoryId} parameter on the method) does NOT throw
 * NPE when the service is invoked from within an active request scope. Prior to the fix,
 * {@link io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodImplementationSupport#memoryId}
 * would use the request-scope state object as the memory id (via
 * {@link io.quarkiverse.langchain4j.runtime.RequestScopeStateDefaultMemoryIdProvider}), and then
 * call {@code chatMemories.computeIfAbsent(memoryId, chatMemoryProvider::get)} with a null
 * {@code chatMemoryProvider}, causing an NPE.
 */
public class QuarkusLangchain4jChatMemoryNpeTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    static final InMemoryChatMemoryStore STORE = new InMemoryChatMemoryStore();

    @Singleton
    static class SharedMemoryStoreProducer {
        @jakarta.enterprise.inject.Produces
        @Singleton
        ChatMemoryStore chatMemoryStore() {
            return STORE;
        }
    }

    @Singleton
    static class SharedMemoryChatMemoryHolder {
        static final ChatMemory MEMORY = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .chatMemoryStore(STORE)
                .build();
    }

    @RegisterAiService
    @Singleton
    interface SharedMemoryChatBot {

        String chat(@UserMessage String userMessage);
    }

    @Inject
    SharedMemoryChatBot sharedMemoryChatBot;

    @Test
    void should_not_throw_npe_with_single_shared_memory_in_request_scope() throws IOException {
        // First call — should NOT throw NPE even though we are inside an active request scope
        setChatCompletionMessageContent("Hi Alice! Nice to meet you.");
        String response1 = sharedMemoryChatBot.chat("Hello, my name is Alice");

        assertThat(response1).isEqualTo("Hi Alice! Nice to meet you.");

        // Verify the message was stored in the chat memory
        assertThat(STORE.getMessages(dev.langchain4j.memory.chat.MessageWindowChatMemory.DEFAULT))
                .hasSize(2)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(
                        tuple(USER, "Hello, my name is Alice"),
                        tuple(AI, "Hi Alice! Nice to meet you."));

        // Second call — verify the bot remembers Alice (shared memory works)
        resetRequests();
        setChatCompletionMessageContent("Your name is Alice");
        String response2 = sharedMemoryChatBot.chat("What is my name?");

        assertThat(response2).isEqualTo("Your name is Alice");

        // Verify that now we have 4 messages (2 more added from second call)
        List<ChatMessage> messages = STORE
                .getMessages(dev.langchain4j.memory.chat.MessageWindowChatMemory.DEFAULT);
        assertThat(messages).hasSize(4);
        assertThat(messages.get(2).type()).isEqualTo(USER);
        assertThat(messages.get(3).type()).isEqualTo(AI);

        // Third call — still no NPE
        resetRequests();
        setChatCompletionMessageContent("Hello again!");
        String response3 = sharedMemoryChatBot.chat("Hello again!");
        assertThat(response3).isEqualTo("Hello again!");

        // Verify message count grew to 6
        assertThat(STORE.getMessages(dev.langchain4j.memory.chat.MessageWindowChatMemory.DEFAULT))
                .hasSize(6);
    }
}
