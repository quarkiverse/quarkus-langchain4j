package io.quarkiverse.langchain4j.memorystore.infinispan.test;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.ChatMemoryRemover;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.memorystore.InfinispanChatMemoryStore;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.runtime.LangChain4jUtil;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

public class InfinispanChatMemoryStoreTest extends OpenAiBaseTest {

    public static final int FIRST_MEMORY_ID = 1;
    public static final int SECOND_MEMORY_ID = 2;

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @BeforeEach
    void setUp() {
        wiremock().resetRequests();
        wiremock().resetMappings();
    }

    @RegisterAiService
    @ApplicationScoped
    interface ChatWithSeparateMemoryForEachUser {

        String chat(@MemoryId int memoryId, @UserMessage String userMessage);
    }

    @Inject
    ChatMemoryStore chatMemoryStore;

    @Inject
    RemoteCacheManager remoteCacheManager;

    @Inject
    ChatWithSeparateMemoryForEachUser chatWithSeparateMemoryForEachUser;

    @Test
    void should_keep_separate_chat_memory_for_each_user_in_store() throws IOException {
        assertThat(chatMemoryStore).isInstanceOf(InfinispanChatMemoryStore.class);

        /* **** First request for user 1 **** */
        String firstMessageFromFirstUser = "Hello, my name is Klaus";
        setChatCompletionMessageContent("Nice to meet you Klaus");
        String firstAiResponseToFirstUser = chatWithSeparateMemoryForEachUser.chat(FIRST_MEMORY_ID,
                firstMessageFromFirstUser);

        assertThat(firstAiResponseToFirstUser).isEqualTo("Nice to meet you Klaus");

        assertSingleRequestMessage(getRequestAsMap(), firstMessageFromFirstUser);

        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).hasSize(2)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromFirstUser), tuple(AI, firstAiResponseToFirstUser));

        resetRequests();

        String firstMessageFromSecondUser = "Hello, my name is Francine";
        setChatCompletionMessageContent("Nice to meet you Francine");
        String firstAiResponseToSecondUser = chatWithSeparateMemoryForEachUser.chat(SECOND_MEMORY_ID,
                firstMessageFromSecondUser);

        assertThat(firstAiResponseToSecondUser).isEqualTo("Nice to meet you Francine");

        assertSingleRequestMessage(getRequestAsMap(), firstMessageFromSecondUser);

        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).hasSize(2)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromSecondUser), tuple(AI, firstAiResponseToSecondUser));

        resetRequests();

        String secondsMessageFromFirstUser = "What is my name?";
        setChatCompletionMessageContent("Your name is Klaus");
        String secondAiMessageToFirstUser = chatWithSeparateMemoryForEachUser.chat(FIRST_MEMORY_ID,
                secondsMessageFromFirstUser);

        assertThat(secondAiMessageToFirstUser).contains("Klaus");

        assertMultipleRequestMessage(getRequestAsMap(),
                List.of(
                        new MessageContent("user", firstMessageFromFirstUser),
                        new MessageContent("assistant", firstAiResponseToFirstUser),
                        new MessageContent("user", secondsMessageFromFirstUser)));

        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).hasSize(4)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromFirstUser), tuple(AI, firstAiResponseToFirstUser),
                        tuple(USER, secondsMessageFromFirstUser), tuple(AI, secondAiMessageToFirstUser));

        resetRequests();

        String secondsMessageFromSecondUser = "What is my name?";
        setChatCompletionMessageContent("Your name is Francine");
        String secondAiMessageToSecondUser = chatWithSeparateMemoryForEachUser.chat(SECOND_MEMORY_ID,
                secondsMessageFromSecondUser);

        assertThat(secondAiMessageToSecondUser).contains("Francine");

        assertMultipleRequestMessage(getRequestAsMap(),
                List.of(
                        new MessageContent("user", firstMessageFromSecondUser),
                        new MessageContent("assistant", firstAiResponseToSecondUser),
                        new MessageContent("user", secondsMessageFromSecondUser)));

        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).hasSize(4)
                .extracting(ChatMessage::type, LangChain4jUtil::chatMessageToText)
                .containsExactly(tuple(USER, firstMessageFromSecondUser), tuple(AI, firstAiResponseToSecondUser),
                        tuple(USER, secondsMessageFromSecondUser), tuple(AI, secondAiMessageToSecondUser));

        // assert our Infinispan cache contains the entries
        assertThat(remoteCacheManager.getCache("chat-memory").containsKey("" + FIRST_MEMORY_ID)).isTrue();
        assertThat(remoteCacheManager.getCache("chat-memory").containsKey("" + SECOND_MEMORY_ID)).isTrue();

        // remove the first entry
        ChatMemoryRemover.remove(chatWithSeparateMemoryForEachUser, FIRST_MEMORY_ID);
        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).isEmpty();
        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).isNotEmpty();

        // remove the second entry
        ChatMemoryRemover.remove(chatWithSeparateMemoryForEachUser, SECOND_MEMORY_ID);
        assertThat(chatMemoryStore.getMessages(FIRST_MEMORY_ID)).isEmpty();
        assertThat(chatMemoryStore.getMessages(SECOND_MEMORY_ID)).isEmpty();

        // now assert that our store was used for delete
        assertThat(remoteCacheManager.getCache("chat-memory").containsKey("" + FIRST_MEMORY_ID)).isFalse();
        assertThat(remoteCacheManager.getCache("chat-memory").containsKey("" + SECOND_MEMORY_ID)).isFalse();
    }

    @Test
    void should_round_trip_all_message_types() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("call-1")
                .name("getWeather")
                .arguments("{\"city\":\"Sao Paulo\"}")
                .build();

        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a helpful assistant"),
                dev.langchain4j.data.message.UserMessage.from("What is the weather in Sao Paulo?"),
                AiMessage.from(toolRequest),
                ToolExecutionResultMessage.from(toolRequest, "25 degrees"),
                AiMessage.from("It is 25 degrees in Sao Paulo"));

        String memoryId = "all-types-test";
        chatMemoryStore.updateMessages(memoryId, messages);

        assertThat(chatMemoryStore.getMessages(memoryId)).containsExactlyElementsOf(messages);

        chatMemoryStore.deleteMessages(memoryId);
        assertThat(chatMemoryStore.getMessages(memoryId)).isEmpty();
    }

    @Test
    void should_persist_ai_message_thinking_field() {
        assertThat(chatMemoryStore).isInstanceOf(InfinispanChatMemoryStore.class);

        AiMessage messageWithThinking = AiMessage.builder()
                .text("The answer is 42")
                .thinking("Let me reason through this carefully. The question asks about the meaning of life...")
                .build();

        int memoryId = 999;

        chatMemoryStore.updateMessages(memoryId, List.of(messageWithThinking));

        List<ChatMessage> retrievedMessages = chatMemoryStore.getMessages(memoryId);

        assertThat(retrievedMessages).hasSize(1);
        ChatMessage retrievedMessage = retrievedMessages.get(0);
        assertThat(retrievedMessage).isInstanceOf(AiMessage.class);

        AiMessage retrievedAiMessage = (AiMessage) retrievedMessage;
        assertThat(retrievedAiMessage.text()).isEqualTo("The answer is 42");
        assertThat(retrievedAiMessage.thinking()).isEqualTo(
                "Let me reason through this carefully. The question asks about the meaning of life...");

        chatMemoryStore.deleteMessages(memoryId);
    }
}
